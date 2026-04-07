package org.puppylab.mypassword.core.web;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.puppylab.mypassword.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;

public class DispatcherService {

    public static final Object NOT_PROCESSED = new Object();
    final Logger               logger        = LoggerFactory.getLogger(getClass());

    List<Dispatcher> getDispatchers  = new ArrayList<>();
    List<Dispatcher> postDispatchers = new ArrayList<>();

    public DispatcherService(Object controller) {
        logger.info("init {}.", getClass().getName());
        Class<?> clazz = controller.getClass();
        for (Method method : clazz.getMethods()) {
            GetMapping get = method.getAnnotation(GetMapping.class);
            PostMapping post = method.getAnnotation(PostMapping.class);
            if (get != null && post != null) {
                throw new IllegalArgumentException("Both @Get and @Post present.");
            }
            if (get != null || post != null) {
                checkMethod(method);
            }
            if (get != null) {
                this.getDispatchers.add(new Dispatcher(controller, method, get.value()));
            }
            if (post != null) {
                this.postDispatchers.add(new Dispatcher(controller, method, post.value()));
            }
        }
    }

    void checkMethod(Method m) {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new IllegalArgumentException("Cannot do URL mapping to static method: " + m);
        }
        m.setAccessible(true);
    }

    public Object processHttpRequest(HttpExchange exchange, String method, String path, String query, String body) {
        List<Dispatcher> dispatchers = null;
        if ("GET".equals(method)) {
            dispatchers = this.getDispatchers;
        } else if ("POST".equals(method)) {
            dispatchers = this.postDispatchers;
        }
        if (dispatchers == null) {
            return NOT_PROCESSED;
        }
        for (Dispatcher dispatcher : dispatchers) {
            Object resp = dispatcher.process(method, path, query, body);
            if (resp != NOT_PROCESSED) {
                return resp;
            }
        }
        return NOT_PROCESSED;
    }

    static class Dispatcher {
        static final Pattern QUERY_SPLIT = Pattern.compile("\\&");

        final Logger logger = LoggerFactory.getLogger(getClass());

        Object  controller;
        Pattern urlPattern;
        Method  handlerMethod;
        Param[] methodParameters;

        public Dispatcher(Object controller, Method method, String urlPattern) {
            this.controller = controller;
            this.urlPattern = compilePath(urlPattern);
            this.handlerMethod = method;
            Parameter[] params = method.getParameters();
            Annotation[][] paramsAnnos = method.getParameterAnnotations();
            this.methodParameters = new Param[params.length];
            for (int i = 0; i < params.length; i++) {
                this.methodParameters[i] = new Param(method, params[i], paramsAnnos[i]);
            }
            logger.info("mapping {} to method: {}", urlPattern, method.getName());
        }

        Pattern compilePath(String path) {
            String regPath = path.replaceAll("\\{([a-zA-Z][a-zA-Z0-9]*)\\}", "(?<$1>[^/]*)");
            if (regPath.indexOf('{') >= 0 || regPath.indexOf('}') >= 0) {
                throw new IllegalArgumentException("Invalid path: " + path);
            }
            return Pattern.compile("^" + regPath + "$");
        }

        Object process(String method, String path, String query, String body) {
            Map<String, String> parsedQuery = null;
            Matcher matcher = urlPattern.matcher(path);
            if (matcher.matches()) {
                Object[] arguments = new Object[this.methodParameters.length];
                for (int i = 0; i < arguments.length; i++) {
                    Param param = methodParameters[i];
                    arguments[i] = switch (param.paramType) {
                    case PATH_VARIABLE -> {
                        String s = matcher.group(param.name);
                        yield convertToType(param.classType, s);
                    }
                    case REQUEST_BODY -> {
                        if (body == null || body.isEmpty()) {
                            yield null;
                        }
                        yield JsonUtils.fromJson(body, param.classType);
                    }
                    case REQUEST_PARAM -> {
                        if (parsedQuery == null) {
                            parsedQuery = parseQuery(query);
                        }
                        String s = parsedQuery.get(param.name);
                        yield convertToType(param.classType, s);
                    }
                    };
                }
                Object result = null;
                try {
                    result = this.handlerMethod.invoke(this.controller, arguments);
                } catch (InvocationTargetException e) {
                    Throwable t = e.getCause();
                    if (t instanceof RuntimeException ex) {
                        throw ex;
                    }
                    throw new RuntimeException(e);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
                return result;
            }
            return NOT_PROCESSED;
        }

        Map<String, String> parseQuery(String query) {
            if (query == null || query.isEmpty()) {
                return Map.of();
            }
            String[] ss = QUERY_SPLIT.split(query);
            Map<String, String> map = new HashMap<>();
            for (String s : ss) {
                int n = s.indexOf('=');
                if (n >= 1) {
                    String key = s.substring(0, n);
                    String value = s.substring(n + 1);
                    map.put(key, URLDecoder.decode(value, StandardCharsets.UTF_8));
                }
            }
            return map;
        }

        Object convertToType(Class<?> classType, String s) {
            if (classType == String.class) {
                return s;
            } else if (classType == boolean.class || classType == Boolean.class) {
                return s == null ? Boolean.FALSE : Boolean.valueOf(s);
            } else if (classType == int.class || classType == Integer.class) {
                return s == null ? Integer.valueOf(0) : Integer.valueOf(s);
            } else if (classType == long.class || classType == Long.class) {
                return s == null ? Long.valueOf(0) : Long.valueOf(s);
            } else {
                throw new IllegalArgumentException("Could not determine argument type: " + classType);
            }
        }
    }

    static enum ParamType {
        PATH_VARIABLE, REQUEST_PARAM, REQUEST_BODY;
    }

    static class Param {

        String    name;
        ParamType paramType;
        Class<?>  classType;

        public Param(Method method, Parameter parameter, Annotation[] annotations) {
            PathVariable pv = getAnnotation(annotations, PathVariable.class);
            RequestParam rp = getAnnotation(annotations, RequestParam.class);
            RequestBody rb = getAnnotation(annotations, RequestBody.class);
            // should only have 1 annotation:
            int total = (pv == null ? 0 : 1) + (rp == null ? 0 : 1) + (rb == null ? 0 : 1);
            if (total > 1) {
                throw new IllegalArgumentException(
                        "Annotation @PathVariable, @RequestParam and @RequestBody cannot be combined at method: "
                                + method);
            }
            this.classType = parameter.getType();
            if (pv != null) {
                this.name = pv.value();
                this.paramType = ParamType.PATH_VARIABLE;
            } else if (rp != null) {
                this.name = rp.value();
                this.paramType = ParamType.REQUEST_PARAM;
            } else if (rb != null) {
                this.paramType = ParamType.REQUEST_BODY;
            } else {
                throw new IllegalArgumentException(
                        "(Missing annotation?) Unsupported argument type: " + classType + " at method: " + method);
            }
        }

        @SuppressWarnings("unchecked")
        <A extends Annotation> A getAnnotation(Annotation[] annos, Class<A> annoClass) {
            for (Annotation anno : annos) {
                if (annoClass.isInstance(anno)) {
                    return (A) anno;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return "Param [name=" + name + ", paramType=" + paramType + ", classType=" + classType + "]";
        }
    }
}
