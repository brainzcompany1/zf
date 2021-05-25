<%@ page import="com.brainzsquare.zf.servlet.MyServletBase" %>
<%@ page import="com.brainzsquare.zf.servlet.MyTestServlet" %>
<%@ page import="com.brainzsquare.bcommons.servlet.ServletUtil" %>
<%@ page import="org.slf4j.Logger" %>


<html>
<head>
    <title>zf test</title>
</head>
<body>
<%
    final Logger logger = MyServletBase.Companion.getLogger();
    final String forecastInput = ServletUtil.INSTANCE.getAttributeString(
        logger, request, MyTestServlet.Companion.getSpForecastInput());
    final String forecastResult = ServletUtil.INSTANCE.getAttributeString(
        logger, request, MyTestServlet.Companion.getSpForecastResult());

    final String evalInput = ServletUtil.INSTANCE.getAttributeString(
        logger, request, MyTestServlet.Companion.getSpEvalInput());
    final String evalResult = ServletUtil.INSTANCE.getAttributeString(
        logger, request, MyTestServlet.Companion.getSpEvalResult());

    final String appPath = request.getContextPath() + "/test";
%>
<form action="<%=appPath%>" method="POST">
    Test forecast<br/>
    <input type="hidden" name="<%=MyTestServlet.Companion.getSpForecast()%>" value="1" />
    <textarea name="<%=MyTestServlet.Companion.getSpForecastInput()%>" cols="120" rows="10"><%=forecastInput%></textarea>
    <br/>
    <input type="submit" value="Forecast">
    <br/><br/>
    Result<br/>
    <textarea readonly name="<%=MyTestServlet.Companion.getSpForecastResult()%>" cols="120" rows="10"><%=forecastResult%></textarea>
    <br/>
</form>
<br/><br/>
<hr/>
<br/><br/>
<form action="<%=appPath%>" method="POST">
    Evaluate with R<br/>
    <input type="hidden" name="<%=MyTestServlet.Companion.getSpEval()%>" value="1" />
    <textarea name="<%=MyTestServlet.Companion.getSpEvalInput()%>" cols="120" rows="10"><%=evalInput%></textarea>
    <br/>
    <input type="submit" value="Evaluate">
    <br/><br/>
    Result<br/>
    <textarea readonly name="<%=MyTestServlet.Companion.getSpEvalResult()%>" cols="120" rows="10"><%=evalResult%></textarea>
    <br/>
</form>
</body>
</html>
