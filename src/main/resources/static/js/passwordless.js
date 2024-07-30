var selPasswordNo = 1;	// 1:password, 2:passwordless, 3:passwordless manage

var str_login = "";
var str_cancel = "";
var str_title_password = "";
var str_title_passwordless = "";
var str_passwordless_regunreg = "";
var str_passwordress_notreg = "";
var str_input_id = "";
var str_input_password = "";
var str_passwordless_blocked = "";
var str_login_expired = "";
var str_login_refused = "";
var str_qrreg_expired = "";
var str_passwordless_unreg = "";
var str_try = "";

$(document).ready(function() {
	passwordless = window.localStorage.getItem('passwordless');
	
	if(passwordless != "Y")
		selPassword(1);
	else
		selPassword(2);
	
	$("#id").focus();
})

function trim(stringToTrim) {
	if(stringToTrim != "")
    	return stringToTrim.replaceAll(/^\s+|\s+$/g,"");
    else
    	return stringToTrim;
}

// Password 로그인 & Passwordless 로그인 선택 radio 버튼
function selPassword(sel) {
	if(sel == 1) {
		selPasswordNo = 1;
		$("#login_title").html(str_title_password);
		$("#selLogin1").prop("checked", true);
		$("#selLogin2").prop("checked", false);
		$("#pw").attr("placeholder", "PASSWORD");
		$("#pw").attr("disabled", false);
		$("#login_bottom1").show();
		$("#login_bottom2").hide();
		
		window.localStorage.removeItem('passwordless');
	}
	else if(sel == 2) {
		selPasswordNo = 2;
		$("#login_title").html(str_title_passwordless);
		$("#selLogin1").prop("checked", false);
		$("#selLogin2").prop("checked", true);
		$("#pw").val("");
		$("#pw").attr("placeholder", "");
		$("#pw").attr("disabled", true);
		$("#login_bottom1").hide();
		$("#login_bottom2").show();
		
		window.localStorage.setItem('passwordless', 'Y');
	}
	
	$("#passwordlessSelButton").show();
	$("#login_bottom").show();
	$("#manage_bottom").hide();
	$("#passwordlessNotice").hide();
	$("#btn_login").html(str_login);
}

// 로그인 요청
function login() {
	id = $("#id").val();
	pw = $("#pw").val();
	
	id = trim(id);
	pw = trim(pw);
	
	$("#id").val(id);
	$("#pw").val(pw);
	
	if(id == "") {
		alert(str_input_id);
		$("#id").focus();
		return false;
	}

	// Password 로그인
	if(selPasswordNo == 1) {
		if(pw == "") {
			alert(str_input_password);
			$("#pw").focus();
			return false;
		}
		
		$.ajax({
	        url : "/api/Login/loginCheck",
	        type : "post",
	        data : {
	        	"id" : $('#id').val().trim(),
	            "pw" : $('#pw').val().trim()
	        },
	        success : function(res) {
	        	if(res.result == "OK") {
	            	location.href = "/main.do";
	        	}
	            else {
	            	alert(res.result);
	            	$("#pw").val("");
	            }
	        },
	        error : function(res) {
	            alert(res.msg);
	        },
	        complete : function() {
	        }
	    });
	}
	// Passwordless 로그인
	else if(selPasswordNo == 2) {
		loginPasswordless();
	}
	// Passwordless manage
	else if(selPasswordNo == 3) {
		managePasswordless();
	}
}

// Passwordless 로그인 요청
function loginPasswordless() {
	$.ajax({
        url : "/api/Login/passwordlesslogin",
        type : "post",
        data : {
        	"id" : $('#id').val().trim()
        },
        success : function(res) {
        	if(res.result == "OK") {
            	var jwt = res.jwt;
            	var url = res.url;
            	//console.log("jwt [" + jwt + "]");
            	//console.log("url [" + url + "]");
            	location.href = url + "?jwt=" + jwt;
        	}
            else {
            	alert(res.result);
            	$("#pw").val("");
            }
        },
        error : function(res) {
            alert(res.msg);
        },
        complete : function() {
        }
    });
}

// passwordless 관리페이지 이동
function moveManagePasswordless() {
	selPasswordNo = 3;
	$("#passwordlessSelButton").hide();
	$("#login_bottom").hide();
	$("#manage_bottom").show();
	$("#passwordlessNotice").show();
	$("#login_title").html(str_passwordless_regunreg);
	$("#btn_login").html(str_passwordless_regunreg);
	$("#pw").attr("placeholder", "PASSWORD");
	$("#pw").attr("disabled", false);
	$("#login_bottom2").hide();
}

function cancelManage() {
	selPassword(2);
}

// 로그인화면으로 이동
function backManagePasswordless() {
	passwordless = window.localStorage.getItem('passwordless');
	
	if(passwordless != "Y")
		selPassword(1);
	else
		selPassword(2);
}

// Passwordless 관리요청
function managePasswordless() {

	id = $("#id").val();
	pw = $("#pw").val();
	
	id = trim(id);
	pw = trim(pw);
	
	$("#id").val(id);
	$("#pw").val(pw);
	
	if(id == "") {
		alert(str_input_id);
		$("#id").focus();
		return false;
	}

	if(pw == "") {
		alert(str_input_password);
		$("#pw").focus();
		return false;
	}
	
	$.ajax({
        url : "/api/Login/passwordlessmanage",
        type : "post",
        data : {
        	"id" : $('#id').val().trim(),
            "pw" : $('#pw').val().trim()
        },
        success : function(res) {
        	if(res.result == "OK") {
            	var jwt = res.jwt;
            	var url = res.url;
            	console.log("jwt [" + jwt + "]");
            	console.log("url [" + url + "]");
            	location.href = url + "?jwt=" + jwt;
        	}
            else {
            	alert(res.result);
            	$("#pw").val("");
            }
        },
        error : function(res) {
            alert(res.msg);
        },
        complete : function() {
        }
    });
}

//도움말
var showHelp = false;
function show_help() {
	if(showHelp == false) {
		$(".pwless_info").show();
		showHelp = true;
	}
	else {
		hide_help();
	}
}
function hide_help() {
	$(".pwless_info").hide();
	showHelp = false;
}
