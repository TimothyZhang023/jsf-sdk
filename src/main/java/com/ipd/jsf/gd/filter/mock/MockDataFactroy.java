/**
 * Copyright 2004-2048 .
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ipd.jsf.gd.filter.mock;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ipd.jsf.gd.error.RpcException;
import com.ipd.jsf.gd.util.Constants;
import com.ipd.jsf.gd.util.JsonUtils;
import com.ipd.jsf.gd.util.JSFContext;
import com.ipd.jsf.gd.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ipd.fastjson.JSONObject;
import com.ipd.jsf.gd.util.ClassLoaderUtils;
import com.ipd.jsf.gd.util.CommonUtils;

/**
 * Title: Mock数据工程类<br>
 * <p/>
 * Description: 保留了配置mock的接口方法调用结果，提供更新和读取方法<br>
 * <p/>
 */
public class MockDataFactroy {

    /**
     * slf4j Logger for this class
     */
    private final static Logger LOGGER = LoggerFactory.getLogger(MockDataFactroy.class);

    /**
     * 结果缓存 {接口:{ 方法#alias : reslut }}
     */
    private static ConcurrentHashMap<String, Map<String, Object>> resultCache = new ConcurrentHashMap<String, Map<String, Object>>();

    /**
     * 读取结果缓存
     *
     * @param interfaceId
     *         接口
     * @param method
     *         方法
     * @param alias
     *         服务别名
     * @return 结果
     */
    public static Object getResultFromCache(String interfaceId, String method, String alias) {
        Map<String, Object> resultMap = resultCache.get(interfaceId);
        return resultMap != null ? resultMap.get(buildKey(method, alias)) : null;
    }

    private static String buildKey(String method, String alias) {
        return method + "#" + alias;
    }

    /**
     * 更新接口下全部模拟结果
     *
     * @param interfaceId
     *         接口名称
     */
    public static void updateCache(String interfaceId) {
        // 从注册中心拿到的结果
        String string = JSFContext.getInterfaceVal(interfaceId, Constants.SETTING_INVOKE_MOCKRESULT, null);
        // 先清空
        if (resultCache.containsKey(interfaceId)) {
            LOGGER.info("Clear mock result of {}", interfaceId);
            resultCache.remove(interfaceId);
        }
        // 再解析
        if (StringUtils.isNotEmpty(string)) {
            /* IP和是否启用已经在注册中心过滤 所以不下发了
            [
                {
                    "method":"echoStr",
                    "alias":"ZG",
                    "mockValue":"\"mockvalue of zg\""
                },
                {
                    "method":"",
                    "alias":"",
                    "mockValue":""
                }
            ]
            */
            List<JSONObject> mocks = null;
            try {
                mocks = JsonUtils.parseObject(string, List.class);
            } catch (Exception e) {
                LOGGER.error("[JSF-21601]Failed to parse mock data of " + interfaceId
                        + ", the error mock json is " + string, e);
            }
            if(mocks==null){
                return;
            }
            try {
                Class clazz = ClassLoaderUtils.forName(interfaceId);

                for (JSONObject mock : mocks) {
                    String method = (String) mock.get("method");
                    String alias = (String) mock.get("alias");
                    String jsonMockValue = (String) mock.get("mockValue");
                    parseMockValue(clazz, interfaceId, method, alias, jsonMockValue, true);
                }
            } catch(Exception e) {
                LOGGER.error("Failed to update mock result of " + interfaceId, e);
            }
        }
    }

    private static void parseMockValue(Class clazz, String interfaceId, String method, String alias,
                                       String jsonMockValue, boolean open) throws Exception {
        Method matchMethod = null;
        for (Method method1 : clazz.getMethods()) {
            if (method1.getName().equals(method)) {
                matchMethod = method1;
                break;
            }
        }
        if (matchMethod != null) { // 解析结果放入缓存
            Object objMockValue = JsonUtils.parseObjectByType(jsonMockValue, matchMethod.getGenericReturnType());
            if (open) { // 开启
                Map<String, Object> methodCache = resultCache.get(interfaceId);
                if (methodCache == null) {
                    methodCache = CommonUtils.putToConcurrentMap(resultCache, interfaceId, new
                            ConcurrentHashMap<String, Object>());
                }
                methodCache.put(buildKey(method, alias), objMockValue);
                LOGGER.info("Add mock result of " + interfaceId + "." + method + ", alias : " +
                        alias + ", mockvalue : " + jsonMockValue);
            } else { // 关闭
                Map<String, Object> methodCache = resultCache.get(interfaceId);
                if (methodCache != null && methodCache.remove(buildKey(method, alias)) != null) {
                    LOGGER.info("Remove mock result of " + interfaceId + "." + method + ", alias : " + alias);
                }
            }
        } else {
            throw new NoSuchMethodException(interfaceId + "." + method);
        }
    }

    /**
     * 是否保存
     *
     * @param interfaceId
     *         杰克曼
     * @param method
     *         方法
     * @param alias
     *         alias
     * @param jsonMockValue
     *         模拟值
     * @param open
     *         是否保存
     */
    public static void addMockResult(String interfaceId, String method, String alias, String jsonMockValue,
                                     boolean open) {
        try {
            Class clazz = ClassLoaderUtils.forName(interfaceId);
            parseMockValue(clazz, interfaceId, method, alias, jsonMockValue, open);
        } catch (Exception e) {
            throw new RpcException("Failed to update mock result of " + interfaceId + "." + method + ", alias : " +
                    alias + ", mockvalue : " + jsonMockValue, e);
        }
    }
}