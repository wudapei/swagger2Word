package org.word.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.word.model.ModelAttr;
import org.word.model.Request;
import org.word.model.Response;
import org.word.model.Table;
import org.word.service.WordService;
import org.word.utils.JsonUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @Author XiuYin.Cui
 * @Date 2018/1/12
 **/
@SuppressWarnings({"unchecked", "rawtypes"})
@Slf4j
@Service
public class WordServiceImpl implements WordService {

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public Map<String, Object> tableList(String swaggerUrl) {
        Map<String, Object> resultMap = new HashMap<>();
        try {
            String jsonStr = restTemplate.getForObject(swaggerUrl, String.class);
            resultMap = tableListFromString(jsonStr);
            log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> tableListFromString(String jsonStr) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Table> result = new ArrayList<>();
        try {
            Map<String, Object> map = getResultFromString(result, jsonStr);
            Map<String, List<Table>> tableMap = result.stream().parallel().collect(Collectors.groupingBy(Table::getTitle));
            resultMap.put("tableMap", new TreeMap<>(tableMap));
            resultMap.put("info", map.get("info"));

            log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        }
        return resultMap;
    }

    @Override
    public Map<String, Object> tableList(MultipartFile jsonFile) {
        Map<String, Object> resultMap = new HashMap<>();
        List<Table> result = new ArrayList<>();
        try {
            String jsonStr = new String(jsonFile.getBytes());
            resultMap = tableListFromString(jsonStr);
            log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        }
        return resultMap;
    }

    // ?????????????????? ??????????????????????????????????????????????????????
    private Map<String, Object> getResultFromString(List<Table> result, String jsonStr) throws IOException {
        // convert JSON string to Map
        Map<String, Object> map = JsonUtils.readValue(jsonStr, HashMap.class);

        //??????model
        Map<String, ModelAttr> definitinMap = parseDefinitions(map);

        //??????paths
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) map.get("paths");
        if (paths != null) {
            Iterator<Entry<String, Map<String, Object>>> it = paths.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Map<String, Object>> path = it.next();

                Iterator<Entry<String, Object>> it2 = path.getValue().entrySet().iterator();
                // 1.????????????
                String url = path.getKey();

                // 2. ??????????????????????????????????????????????????????????????????????????????
                while (it2.hasNext()) {
                    Entry<String, Object> request = it2.next();

                    // 2. ???????????????????????? get,post,delete,put ??????
                    String requestType = request.getKey();

                    Map<String, Object> content = (Map<String, Object>) request.getValue();

                    // 4. ????????????????????????
                    String title = String.valueOf(((List) content.get("tags")).get(0));

                    // 5.????????? ??????????????????
                    String tag = String.valueOf(content.get("summary"));

                    // 6.????????????
                    String description = String.valueOf(content.get("summary"));

                    // 7.?????????????????????????????? multipart/form-data
                    String requestForm = "";
                    List<String> consumes = (List) content.get("consumes");
                    if (consumes != null && consumes.size() > 0) {
                        requestForm = StringUtils.join(consumes, ",");
                    }

                    // 8.?????????????????????????????? application/json
                    String responseForm = "";
                    List<String> produces = (List) content.get("produces");
                    if (produces != null && produces.size() > 0) {
                        responseForm = StringUtils.join(produces, ",");
                    }

                    // 9. ?????????
                    List<LinkedHashMap> parameters = (ArrayList) content.get("parameters");

                    // 10.?????????
                    Map<String, Object> responses = (LinkedHashMap) content.get("responses");

                    //??????Table
                    Table table = new Table();

                    table.setTitle(title);
                    table.setUrl(url);
                    table.setTag(tag);
                    table.setDescription(description);
                    table.setRequestForm(requestForm);
                    table.setResponseForm(responseForm);
                    table.setRequestType(requestType);
                    table.setRequestList(processRequestList(parameters, definitinMap));
                    table.setResponseList(processResponseCodeList(responses));

                    // ??????????????????200???????????????
                    Map<String, Object> obj = (Map<String, Object>) responses.get("200");
                    if (obj != null && obj.get("schema") != null) {
                        table.setModelAttr(processResponseModelAttrs(obj, definitinMap));
                    }

                    //??????
                    table.setRequestParam(processRequestParam(table.getUrl(), table.getRequestList()));
                    table.setResponseParam(processResponseParam(obj, definitinMap));

                    result.add(table);
                }
            }
        }
        return map;
    }

    //  ?????????????????? ????????? ??????????????????????????????????????????????????????
    /*private Map<String, Object> getResultFromString(List<Table> result, String jsonStr) throws IOException {
        // convert JSON string to Map
        Map<String, Object> map = JsonUtils.readValue(jsonStr, HashMap.class);

        //??????model
        Map<String, ModelAttr> definitinMap = parseDefinitions(map);

        //??????paths
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) map.get("paths");

        //????????????????????????????????????????????????????????????
        List<String> defaultConsumes = (List) map.get("consumes");

        //????????????????????????????????????????????????????????????
        List<String> defaultProduces = (List) map.get("produces");

        if (paths != null) {

            Iterator<Entry<String, Map<String, Object>>> it = paths.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Map<String, Object>> path = it.next();

                // 0. ???????????????????????????????????????????????????
                Map<String, Object> methods = (Map<String, Object>) path.getValue();
                List<LinkedHashMap> commonParameters = (ArrayList) methods.get("parameters");

                Iterator<Entry<String, Object>> it2 = path.getValue().entrySet().iterator();
                // 1.????????????
                String url = path.getKey();

                while (it2.hasNext()) {
                    Entry<String, Object> request = it2.next();

                    // 2.???????????????????????? get,post,delete,put ??????
                    String requestType = request.getKey();

                    if ("parameters".equals(requestType)) {
                        continue;
                    }

                    Map<String, Object> content = (Map<String, Object>) request.getValue();

                    // 4. ????????????????????????
                    String title = String.valueOf(((List) content.get("tags")).get(0));

                    // 5.????????? ??????????????????
                    String tag = String.valueOf(content.get("operationId"));

                    // 6.????????????
                    String description = String.valueOf(content.get("description"));

                    // 7.?????????????????????????????? multipart/form-data
                    String requestForm = "";
                    List<String> consumes = (List) content.get("consumes");
                    if (consumes != null && consumes.size() > 0) {
                        requestForm = StringUtils.join(consumes, ",");
                    } else {
                        requestForm = StringUtils.join(defaultConsumes, ",");
                    }

                    // 8.?????????????????????????????? application/json
                    String responseForm = "";
                    List<String> produces = (List) content.get("produces");
                    if (produces != null && produces.size() > 0) {
                        responseForm = StringUtils.join(produces, ",");
                    } else {
                        responseForm = StringUtils.join(defaultProduces, ",");
                    }

                    // 9. ?????????
                    List<LinkedHashMap> parameters = (ArrayList) content.get("parameters");

                    if (!CollectionUtils.isEmpty(parameters)) {
                        if (commonParameters != null) {
                            parameters.addAll(commonParameters);
                        }
                    } else {
                        if (commonParameters != null) {
                            parameters = commonParameters;
                        }
                    }

                    // 10.?????????
                    Map<String, Object> responses = (LinkedHashMap) content.get("responses");

                    //??????Table
                    Table table = new Table();

                    table.setTitle(title);
                    table.setUrl(url);
                    table.setTag(tag);
                    table.setDescription(description);
                    table.setRequestForm(requestForm);
                    table.setResponseForm(responseForm);
                    table.setRequestType(requestType);
                    table.setRequestList(processRequestList(parameters, definitinMap));
                    table.setResponseList(processResponseCodeList(responses));

                    // ??????????????????200???????????????
                    Map<String, Object> obj = (Map<String, Object>) responses.get("200");
                    if (obj != null && obj.get("schema") != null) {
                        table.setModelAttr(processResponseModelAttrs(obj, definitinMap));
                    }

                    //??????
                    table.setRequestParam(processRequestParam(table.getRequestList()));
                    table.setResponseParam(processResponseParam(obj, definitinMap));

                    result.add(table);
                }
            }
        }
        return map;
    }*/

    /**
     * ????????????????????????
     *
     * @param parameters
     * @param definitinMap
     * @return
     */
    private List<Request> processRequestList(List<LinkedHashMap> parameters, Map<String, ModelAttr> definitinMap) {
        List<Request> requestList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(parameters)) {
            for (Map<String, Object> param : parameters) {
                Object in = param.get("in");
                Request request = new Request();
                request.setName(String.valueOf(param.get("name")));
                request.setType(param.get("type") == null ? "object" : param.get("type").toString());
                if (param.get("format") != null) {
                    request.setType(request.getType() + "(" + param.get("format") + ")");
                }
                request.setParamType(String.valueOf(in));
                // ????????????????????????
                if (in != null && "body".equals(in)) {
                    request.setType(String.valueOf(in));
                    Map<String, Object> schema = (Map) param.get("schema");
                    Object ref = schema.get("$ref");
                    // ????????????????????????
                    if (schema.get("type") != null && "array".equals(schema.get("type"))) {
                        ref = ((Map) schema.get("items")).get("$ref");
                        request.setType("array");
                    }
                    if (ref != null) {
                        request.setType(request.getType() + ":" + ref.toString().replaceAll("#/definitions/", ""));
                        request.setModelAttr(definitinMap.get(ref));
                    }
                }
                // ????????????
                request.setRequire(false);
                if (param.get("required") != null) {
                    request.setRequire((Boolean) param.get("required"));
                }
                // ????????????
                request.setRemark(String.valueOf(param.get("description")));
                requestList.add(request);
            }
        }
        return requestList;
    }


    /**
     * ?????????????????????
     *
     * @param responses ???????????????????????????
     * @return
     */
    private List<Response> processResponseCodeList(Map<String, Object> responses) {
        List<Response> responseList = new ArrayList<>();
        Iterator<Map.Entry<String, Object>> resIt = responses.entrySet().iterator();
        while (resIt.hasNext()) {
            Map.Entry<String, Object> entry = resIt.next();
            Response response = new Response();
            // ????????? 200 201 401 403 404 ??????
            response.setName(entry.getKey());
            LinkedHashMap<String, Object> statusCodeInfo = (LinkedHashMap) entry.getValue();
            response.setDescription(String.valueOf(statusCodeInfo.get("description")));
            Object schema = statusCodeInfo.get("schema");
            if (schema != null) {
                Object originalRef = ((LinkedHashMap) schema).get("originalRef");
                response.setRemark(originalRef == null ? "" : originalRef.toString());
            }
            responseList.add(response);
        }
        return responseList;
    }

    /**
     * ????????????????????????
     *
     * @param responseObj
     * @param definitinMap
     * @return
     */
    private ModelAttr processResponseModelAttrs(Map<String, Object> responseObj, Map<String, ModelAttr> definitinMap) {
        Map<String, Object> schema = (Map<String, Object>) responseObj.get("schema");
        String type = (String) schema.get("type");
        String ref = null;
        //??????
        if ("array".equals(type)) {
            Map<String, Object> items = (Map<String, Object>) schema.get("items");
            if (items != null && items.get("$ref") != null) {
                ref = (String) items.get("$ref");
            }
        }
        //??????
        if (schema.get("$ref") != null) {
            ref = (String) schema.get("$ref");
        }

        //????????????
        ModelAttr modelAttr = new ModelAttr();
        modelAttr.setType(StringUtils.defaultIfBlank(type, StringUtils.EMPTY));

        if (StringUtils.isNotBlank(ref) && definitinMap.get(ref) != null) {
            modelAttr = definitinMap.get(ref);
        }
        return modelAttr;
    }

    /**
     * ??????Definition
     *
     * @param map
     * @return
     */
    private Map<String, ModelAttr> parseDefinitions(Map<String, Object> map) {
        Map<String, Map<String, Object>> definitions = (Map<String, Map<String, Object>>) map.get("definitions");
        Map<String, ModelAttr> definitinMap = new HashMap<>(256);
        if (definitions != null) {
            Iterator<String> modelNameIt = definitions.keySet().iterator();
            while (modelNameIt.hasNext()) {
                String modeName = modelNameIt.next();
                getAndPutModelAttr(definitions, definitinMap, modeName);
            }
        }
        return definitinMap;
    }

    /**
     * ????????????ModelAttr
     * ???$ref????????????????????????
     */
    private ModelAttr getAndPutModelAttr(Map<String, Map<String, Object>> swaggerMap, Map<String, ModelAttr> resMap, String modeName) {
        ModelAttr modeAttr;
        if ((modeAttr = resMap.get("#/definitions/" + modeName)) == null) {
            modeAttr = new ModelAttr();
            resMap.put("#/definitions/" + modeName, modeAttr);
        } else if (modeAttr.isCompleted()) {
            return resMap.get("#/definitions/" + modeName);
        }
        Map<String, Object> modeProperties = (Map<String, Object>) swaggerMap.get(modeName).get("properties");
        if (modeProperties == null) {
            return null;
        }

        List<ModelAttr> attrList = getModelAttrs(swaggerMap, resMap, modeAttr, modeProperties);
        List allOf = (List) swaggerMap.get(modeName).get("allOf");
        if (allOf != null) {
            for (int i = 0; i < allOf.size(); i++) {
                Map c = (Map) allOf.get(i);
                if (c.get("$ref") != null) {
                    String refName = c.get("$ref").toString();
                    //?????? #/definitions/ ?????????
                    String clsName = refName.substring(14);
                    Map<String, Object> modeProperties1 = (Map<String, Object>) swaggerMap.get(clsName).get("properties");
                    List<ModelAttr> attrList1 = getModelAttrs(swaggerMap, resMap, modeAttr, modeProperties1);
                    if (attrList1 != null && attrList != null) {
                        attrList.addAll(attrList1);
                    } else if (attrList == null && attrList1 != null) {
                        attrList = attrList1;
                    }
                }
            }
        }

        Object title = swaggerMap.get(modeName).get("title");
        Object description = swaggerMap.get(modeName).get("description");
        modeAttr.setClassName(title == null ? "" : title.toString());
        modeAttr.setDescription(description == null ? "" : description.toString());
        modeAttr.setProperties(attrList);
        Object required = swaggerMap.get(modeName).get("required");
        if (Objects.nonNull(required)) {
            if ((required instanceof List) && !CollectionUtils.isEmpty(attrList)) {
                List requiredList = (List) required;
                attrList.stream().filter(m -> requiredList.contains(m.getName())).forEach(m -> m.setRequire(true));
            } else if (required instanceof Boolean) {
                modeAttr.setRequire(Boolean.parseBoolean(required.toString()));
            }
        }
        return modeAttr;
    }

    private List<ModelAttr> getModelAttrs(Map<String, Map<String, Object>> swaggerMap, Map<String, ModelAttr> resMap, ModelAttr modeAttr, Map<String, Object> modeProperties) {
        Iterator<Entry<String, Object>> mIt = modeProperties.entrySet().iterator();

        List<ModelAttr> attrList = new ArrayList<>();

        //????????????
        while (mIt.hasNext()) {
            Entry<String, Object> mEntry = mIt.next();
            Map<String, Object> attrInfoMap = (Map<String, Object>) mEntry.getValue();
            ModelAttr child = new ModelAttr();
            child.setName(mEntry.getKey());
            child.setType((String) attrInfoMap.get("type"));
            if (attrInfoMap.get("format") != null) {
                child.setType(child.getType() + "(" + attrInfoMap.get("format") + ")");
            }
            child.setType(StringUtils.defaultIfBlank(child.getType(), "object"));

            Object ref = attrInfoMap.get("$ref");
            Object items = attrInfoMap.get("items");
            if (ref != null || (items != null && (ref = ((Map) items).get("$ref")) != null)) {
                String refName = ref.toString();
                //?????? #/definitions/ ?????????
                String clsName = refName.substring(14);
                modeAttr.setCompleted(true);
                ModelAttr refModel = getAndPutModelAttr(swaggerMap, resMap, clsName);
                if (refModel != null) {
                    child.setProperties(refModel.getProperties());
                }
                child.setType(child.getType() + ":" + clsName);
            }
            child.setDescription((String) attrInfoMap.get("description"));
            attrList.add(child);
        }
        return attrList;
    }

    /**
     * ???????????????
     *
     * @param responseObj
     * @return
     */
    private String processResponseParam(Map<String, Object> responseObj, Map<String, ModelAttr> definitinMap) throws JsonProcessingException {
        if (responseObj != null && responseObj.get("schema") != null) {
            Map<String, Object> schema = (Map<String, Object>) responseObj.get("schema");
            String type = (String) schema.get("type");
            String ref = null;
            // ??????
            if ("array".equals(type)) {
                Map<String, Object> items = (Map<String, Object>) schema.get("items");
                if (items != null && items.get("$ref") != null) {
                    ref = (String) items.get("$ref");
                }
            }
            // ??????
            if (schema.get("$ref") != null) {
                ref = (String) schema.get("$ref");
            }
            if (StringUtils.isNotEmpty(ref)) {
                ModelAttr modelAttr = definitinMap.get(ref);
                if (modelAttr != null && !CollectionUtils.isEmpty(modelAttr.getProperties())) {
                    Map<String, Object> responseMap = new HashMap<>(8);
                    for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                        responseMap.put(subModelAttr.getName(), getValue(subModelAttr.getName(), subModelAttr.getType(), subModelAttr));
                    }
                    return JsonUtils.writeJsonStr(responseMap);
                }
            }
        }
        return StringUtils.EMPTY;
    }

    /**
     * ???????????????
     *
     * @param list
     * @return
     */
    private String processRequestParam(String url, List<Request> list) throws IOException {
        Map<String, Object> headerMap = new LinkedHashMap<>();
        Map<String, Object> queryMap = new LinkedHashMap<>();
        Map<String, Object> jsonMap = new LinkedHashMap<>();
        if (list != null && list.size() > 0) {
            for (Request request : list) {
                String name = request.getName();
                String paramType = request.getParamType();
                Object value = getValue(request.getType(), request.getModelAttr());
                switch (paramType) {
                    case "header":
                        headerMap.put(name, value);
                        break;
                    case "query":
                    case "path":
                        queryMap.put(name, value);
                        break;
                    case "body":
                        jsonMap.put(name, value);
                        break;
                    default:
                        break;

                }
            }
        }
        String res = "";
        if (!queryMap.isEmpty()) {
            res += getUrlParamsByMap(url, queryMap);
        }
        if (!headerMap.isEmpty()) {
            res += " " + getHeaderByMap(headerMap);
        }
        if (!jsonMap.isEmpty()) {
            if (jsonMap.size() == 1) {
                for (Entry<String, Object> entry : jsonMap.entrySet()) {
//                    res += " -d '" + JsonUtils.writeJsonStr(entry.getValue()) + "'";
                    res +=  JsonUtils.writeJsonStr(entry.getValue());
                }
            } else {
//                res += " -d '" + JsonUtils.writeJsonStr(jsonMap) + "'";
                res += JsonUtils.writeJsonStr(jsonMap);
            }
        }
        return res;
    }

    /**
     * ??????????????????????????????
     * @param name      ?????????
     * @param type      ??????
     * @param modelAttr ???????????????
     * @return
     */
    private Object getValue(String name, String type, ModelAttr modelAttr) {
        if(StringUtils.isNotBlank(name)) {
            if ("OpCode".equalsIgnoreCase(name)) {
                return 0;
            } else if ("StatusCode".equalsIgnoreCase(name)) {
                return 200;
            } else if ("OpDesc".equalsIgnoreCase(name)) {
                return "??????";
            } else if ("PageNum".equalsIgnoreCase(name)) {
                return 1;
            } else if ("PageSize".equalsIgnoreCase(name)) {
                return 20;
            } else if ("Total".equalsIgnoreCase(name)) {
                return 2356;
            } else {
                getValue(type, modelAttr);
            }
        }
        return getValue(type, modelAttr);
    }

    /**
     * ?????????????????????
     * @param type          ????????????
     * @param modelAttr     ????????????
     * @return              Object
     */
    private Object getValue(String type, ModelAttr modelAttr) {
        int pos;
        if ((pos = type.indexOf(":")) != -1) {
            type = type.substring(0, pos);
        }
        switch (type) {
            case "string":
                return "test";
            case "string(date-time)":
                return "2020-01-01 00:00:00";
            case "integer":
            case "integer(int64)":
            case "integer(int32)":
                return 1;
            case "number":
                return 2.3;
            case "boolean":
                return true;
            case "file":
                return "(binary)";
            case "array":
                List list = new ArrayList();
                Map<String, Object> map = new LinkedHashMap<>();
                if (modelAttr != null && !CollectionUtils.isEmpty(modelAttr.getProperties())) {
                    for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                        map.put(subModelAttr.getName(), getValue(subModelAttr.getType(), subModelAttr));
                    }
                }
                list.add(map);
                return list;
            case "object":
            case "body":
                map = new LinkedHashMap<>();
                if (modelAttr != null && !CollectionUtils.isEmpty(modelAttr.getProperties())) {
                    for (ModelAttr subModelAttr : modelAttr.getProperties()) {
                        map.put(subModelAttr.getName(), getValue(subModelAttr.getType(), subModelAttr));
                    }
                }
                return map;
            default:
                return null;
        }
    }

    /**
     * getUrlParamsByMap
     * @param url   url
     * @param map   ??????
     * @return      ??????url
     */
    private static String getUrlParamsByMap(String url, Map<String, Object> map) {
        if (CollectionUtils.isEmpty(map)) {
            return "";
        }
        String urlPrefix = "http://127.0.0.1:8080";
        if (StringUtils.isNotBlank(url)) {
            if (url.contains("{") && url.contains("}")) {
                String prefix = url.substring(0, url.indexOf("{"));
                String pathParam = url.substring(url.indexOf("{") + 1, url.length() -1);
                log.info("url={}, pathParam={}", url, pathParam);
                Object value = map.get(pathParam);
                if (Objects.nonNull(value)) {
                    url = urlPrefix + prefix + value;
                    map.remove(pathParam);
                }
            }else{
                url = urlPrefix + url + "?";
            }
        }
        StringBuilder sBuilder = new StringBuilder(url);
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sBuilder.append(entry.getKey()).append("=").append(entry.getValue());
            sBuilder.append("&");
        }
        String s = sBuilder.toString();
        if (s.endsWith("&")) {
            s = StringUtils.substringBeforeLast(s, "&");
        }
        return s;
    }

    /**
     * ???map?????????header
     */
    public static String getHeaderByMap(Map<String, Object> map) {
        if (CollectionUtils.isEmpty(map)) {
            return "";
        }
        StringBuilder sBuilder = new StringBuilder();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sBuilder.append("--header '");
            sBuilder.append(entry.getKey() + ":" + entry.getValue());
            sBuilder.append("'");
        }
        return sBuilder.toString();
    }
}
