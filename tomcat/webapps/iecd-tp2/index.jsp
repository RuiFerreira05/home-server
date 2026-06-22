<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    // Redirect standard index landing queries to /dashboard.
    // The AuthFilter will intercept unauthenticated requests and route them 
    // to /auth/login seamlessly.
    response.sendRedirect(request.getContextPath() + "/dashboard");
%>