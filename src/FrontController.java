package controller;

import annotation.*;
import utils.Mapping;
import utils.ModelView;
import utils.*;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import exception.*;

public class FrontController extends HttpServlet {
    private HashMap<String, Mapping> hashMap;

    @Override
    public void init() throws ServletException {
        try {
            String packageName = this.getInitParameter("nom_package");
            hashMap = Scan.getAllClassSelonAnnotation2(this, packageName, AnnotationController.class);
            System.out.println("Initialization completed. HashMap size: " + hashMap.size());
        } catch (PackageNotFoundException e) {
            throw new ServletException("Initialization error - Package not found: " + e.getMessage(), e);
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();

        String requestURI = request.getRequestURI();
        String contextPath = request.getContextPath();
        String url = requestURI.substring(contextPath.length());

        if (url.contains("?")) {
            int index = url.indexOf("?");
            String basePath = url.substring(0, index);
            url = basePath;
        }

        if (hashMap != null) {
            Mapping mapping = hashMap.get(url);
            if (mapping != null) {
                try {
                    Class<?> clazz = Class.forName(mapping.getClassName());
                    Method method = null;

                    for (Method m : clazz.getDeclaredMethods()) {
                        if (m.getName().equals(mapping.getMethodName())) {
                            method = m;
                            break;
                        }
                    }

                    if (method == null) {
                        throw new NoSuchMethodException(
                                "Method " + mapping.getMethodName() + " not found in " + clazz.getName());
                    }

                    Object instance = clazz.getDeclaredConstructor().newInstance();
                    Object[] parameterValues = Utils.getParameterValues(request, method, Param.class);

                    Object result = method.invoke(instance, parameterValues);
                    if (result instanceof ModelView) {
                        ModelView modelView = (ModelView) result;
                        RequestDispatcher dispatch = request.getRequestDispatcher(modelView.getUrl());
                        HashMap<String, Object> data = modelView.getData();
                        for (String keyData : data.keySet()) {
                            request.setAttribute(keyData, data.get(keyData));
                            System.out.println("Clé :" + keyData);
                            System.out.println("Data :" + data.get(keyData));
                        }
                        dispatch.forward(request, response);
                    } else if (result instanceof String) {
                        out.println(result.toString());
                    } else {
                        out.println("Unsupported return type from controller method.");
                    }
                } catch (Exception e) {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.println("Error invoking method: " + e.getMessage());
                    e.printStackTrace(out);
                }
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No mapping found for URL: " + url);
            }
        } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "HashMap is null. Initialization may have failed.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
}
