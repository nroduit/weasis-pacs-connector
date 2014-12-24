<%
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+request.getContextPath()+"/";
%>
<html>
<head>
<title>Weasis Applet</title>
<base href="<%=basePath%>">

<style media="screen" type="text/css">
* {
	margin: 0;
	padding: 0;
}

html, body {
	-moz-box-sizing: border-box;
	-webkit-box-sizing: border-box;
	box-sizing: border-box;
	background-color: gray;
	display: block;
	position: absolute;
	bottom: 0;
	top: 0;
	left: 0;
	right: 0;
	margin-top: 0px;
	margin-bottom: 0px;
	margin-right: 0px;
	margin-left: 0px;
}

applet {
	display: block;
}
</style>
</head>
<body>
  <object type="application/x-java-applet" width="100%" height="100%">
     <param name="jnlp_href" value="${param.jnlp}">
     <param name="commands" value="${param.commands}">
     No Java support detected, please install the latest version of Java.
   </object>
</body>
</html>