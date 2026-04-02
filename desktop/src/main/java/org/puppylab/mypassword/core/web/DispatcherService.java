package org.puppylab.mypassword.core.web;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.puppylab.mypassword.core.ErrorUtils;
import org.puppylab.mypassword.rpc.BaseRequest;
import org.puppylab.mypassword.rpc.BaseResponse;
import org.puppylab.mypassword.rpc.ErrorCode;
import org.puppylab.mypassword.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DispatcherService {
    final Logger           logger          = LoggerFactory.getLogger(getClass());
    final List<Dispatcher> dispatchers     = new ArrayList<>();
    final ObjectMapper     mapper          = JsonUtils.getObjectMapper();
    final String           errorBadRequest = ErrorUtils.errorJson(ErrorCode.BAD_REQUEST, "Bad request.");
    final String           errorNotFound   = ErrorUtils.errorJson(ErrorCode.BAD_REQUEST, "Not found.");

    public DispatcherService(Object controller) {
        Class<?> clazz = controller.getClass();
        for (Method method : clazz.getMethods()) {
            Post post = method.getAnnotation(Post.class);
            if (post != null) {
                dispatchers.add(new Dispatcher(controller, method));
            }
        }
    }

    public String process(String path, String jsonBody) {
        try {
            Object result = null;
            for (Dispatcher dispatcher : this.dispatchers) {
                result = dispatcher.process(path, jsonBody);
                if (result != null) {
                    break;
                }
            }
            if (result != null) {
                return mapper.writeValueAsString(result);
            }
            return errorNotFound;
        } catch (Exception e) {
            logger.warn(e.getMessage(), e);
        }
        return errorBadRequest;
    }

}

class Dispatcher {

    final Logger  logger = LoggerFactory.getLogger(getClass());
    final Pattern urlPattern;

    final Object controller;

    final Class<?> requestClass;
    final boolean  hasRestParams;

    final Method method;

    Dispatcher(Object controller, Method method) {
        String path = method.getAnnotation(Post.class).value();
        this.urlPattern = compile(path);
        this.controller = controller;
        this.method = method;
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length != 1 && paramTypes.length != 2) {
            throw new IllegalArgumentException("Invalid method parameters.");
        }
        if (!BaseResponse.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException("Invalid method return type: must be BaseResponse or subtype.");
        }
        if (!BaseRequest.class.isAssignableFrom(paramTypes[0])) {
            throw new IllegalArgumentException("Invalid method parameters[0]: must be BaseRequest or subtype.");
        }
        this.hasRestParams = paramTypes.length == 2;
        if (this.hasRestParams && paramTypes[1] != String[].class) {
            throw new IllegalArgumentException("Invalid method parameters[1]: must be String[].");
        }
        this.requestClass = paramTypes[0];
        logger.info("dispatcher path '{}' to {}.{}().", path, controller.getClass().getSimpleName(), method.getName());
    }

    Object process(String path, String jsonBody) throws Exception {
        Matcher matcher = this.urlPattern.matcher(path);
        if (matcher.matches()) {
            Object req = JsonUtils.getObjectMapper().readValue(jsonBody, this.requestClass);
            if (hasRestParams) {
                int count = matcher.groupCount();
                String[] params = new String[count];
                for (int i = 0; i < count; i++) {
                    params[i] = matcher.group(i + 1);
                }
                return method.invoke(controller, req, params);
            } else {
                return method.invoke(controller, req);
            }
        }
        return null;
    }

    Pattern compile(String path) {
        String regPath = path.replaceAll("\\{([a-zA-Z][a-zA-Z0-9]*)\\}", "(?<$1>[^/]*)");
        if (regPath.indexOf('{') >= 0 || regPath.indexOf('}') >= 0) {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
        return Pattern.compile("^" + regPath + "$");
    }
}
