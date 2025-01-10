package com.pwl.api;

import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.pwl.config.MessageUtils;
import com.pwl.domain.Login.UserInfo;
import com.pwl.mapper.Login.LoginMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/Login/")
public class ApiLogin {

	@Autowired
	private LoginMapper loginMapper;
	
	@Autowired
    MessageUtils messageUtils;
	
	@Value("${passwordless.corpId}")
	private String corpId;
	
	@Value("${passwordless.serverId}")
	private String serverId;
	
	@Value("${passwordless.serverKey}")
	private String serverKey;
	
	@Value("${passwordless.simpleAutopasswordUrl}")
	private String simpleAutopasswordUrl;
	
	@Value("${passwordless.simpleCheckUrl}")
	private String simpleCheckUrl;
	
	@Value("${passwordless.recommend}")
	private String recommend;
	
	@Value("${passwordless.restCheckUrl}")
	private String restCheckUrl;
	
	// Passwordless result redirect URL
	private String passwordlessresult = "/Login/passwordlessresult.do";
	
	// Passwordless URLs
	private String isApUrl = "/ap/rest/auth/isAp";									// Passwordless registration check
	private String joinApUrl = "/ap/rest/auth/joinAp";								// Passwordless registration REST API
	private String withdrawalApUrl = "/ap/rest/auth/withdrawalAp";					// Passwordless cancellation REST API
	private String getTokenForOneTimeUrl = "/ap/rest/auth/getTokenForOneTime";		// Passwordless one-time token request REST API
	private String getSpUrl = "/ap/rest/auth/getSp";								// Passwordless authentication request REST API
	private String resultUrl = "/ap/rest/auth/result";								// Passwordless authentication result request REST API
	private String cancelUrl = "/ap/rest/auth/cancel";								// Passwordless authentication cancellation REST API
	
	@PostMapping(value="loginCheck", produces="application/json;charset=utf8")
	public Map<String, Object> loginCheck(
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "pw", required = false) String pw,
			HttpServletRequest request) {
	
		if(id == null)	id = "";
		if(pw == null)	pw = "";

		log.info("loginCheck [" + id + "] / ["  + pw + "]");

		Map<String, Object> mapResult = new HashMap<String, Object>();

		if(!id.equals("") && !pw.equals("")) {
			
			UserInfo userinfo = new UserInfo();
			userinfo.setId(id);
			userinfo.setPw(pw);
			UserInfo newUserinfo = loginMapper.checkPassword(userinfo);
			
			boolean exist = false;

			// Check if user is registered in the Simple UI mode (passwordless.recommend=1 and if user is registered, ID/PASSWORD login is not allowed --> QRCode must be unregistered to allow ID/PASSWORD login)
			if(newUserinfo != null) {
				String existId = passwordlessCallApi(id);
				log.info("recommend=" + recommend + ", existId=" + existId);
				
				if(recommend.equals("1") && existId.equals("T")) {
					mapResult.put("result", messageUtils.getMessage("text.passwordless.password"));	// If you want to log in with your user password,\nunregister the Passwordless service first.
				}
				else {
					HttpSession session = request.getSession(true);
					session.setAttribute("id", id);
					
					mapResult.put("result", "OK");
				}
			}
			else {
				mapResult.put("result", messageUtils.getMessage("text.passwordless.invalid"));	// Invalid id or password.
			}
		}

		log.info("result [" + mapResult.toString() + "]");

		return mapResult;
	}
	
	@PostMapping(value="join", produces="application/json;charset=utf8")
	public Map<String, Object> join(
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "pw", required = false) String pw,
			@RequestParam(value = "email", required = false) String email,
			HttpServletRequest request) {
	
		if(id == null)		id = "";
		if(pw == null)		pw = "";
		if(email == null)	email = "";

		log.info("join [" + id + "] / ["  + pw + "] / ["  + email + "]");

		Map<String, Object> mapResult = new HashMap<String, Object>();

		if(!id.equals("") && !pw.equals("") && !email.equals("")) {
			
			UserInfo userinfo = new UserInfo();
			userinfo.setId(id);
			UserInfo newUserinfo = loginMapper.getUserInfo(userinfo);
			
			if(newUserinfo != null) {
				log.info("join failed");
				String tmp_result = messageUtils.getMessage("text.passwordless.idexist");	// ID [" + id + "] already exists.
				mapResult.put("result", tmp_result.replace("@@@", id));
			}
			else {
				userinfo.setPw(pw);
				userinfo.setEmail(email);
				loginMapper.createUserInfo(userinfo);
				log.info("join completed.");

				mapResult.put("result", "OK");
			}
		}
		
		return mapResult;
	}
	
	@PostMapping(value="withdraw", produces="application/json;charset=utf8")
	public Map<String, Object> withdraw(HttpServletRequest request) {
	
		HttpSession session = request.getSession(true);
		String id = (String) session.getAttribute("id");

		if(id == null)		id = "";

		log.info("withdraw [" + id + "]");

		Map<String, Object> mapResult = new HashMap<String, Object>();

		if(!id.equals("")) {
			
			UserInfo userinfo = new UserInfo();
			userinfo.setId(id);
			loginMapper.withdrawUserInfo(userinfo);
			
			session.setAttribute("id", null);
			log.info("withdraw [" + id + "] completed.");
		}
		
		mapResult.put("result", "OK");

		return mapResult;
	}
	
	@PostMapping(value="changepw", produces="application/json;charset=utf8")
	public Map<String, Object> changepw(
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "pw", required = false) String pw,
			HttpServletRequest request) {
	
		if(id == null)		id = "";
		if(pw == null)		pw = "";

		log.info("changepw [" + id + "] [" + pw + "]");

		Map<String, Object> mapResult = new HashMap<String, Object>();

		if(!id.equals("") && !pw.equals("")) {
			
			UserInfo userinfo = new UserInfo();
			userinfo.setId(id);
			UserInfo newUserinfo = loginMapper.getUserInfo(userinfo);
			
			if(newUserinfo == null) {
				log.info("changepw failed");
				String tmp_result = messageUtils.getMessage("text.passwordless.idnotexist");	// ID [" + id + "] does not exist.
				mapResult.put("result", tmp_result.replace("@@@", id));
			}
			else {
				userinfo.setPw(pw);
				loginMapper.changepw(userinfo);
				log.info("changepw completed.");

				mapResult.put("result", "OK");
			}
		}
		else {
			mapResult.put("result", messageUtils.getMessage("text.passwordless.invalid"));	// Invalid id or password.
		}

		return mapResult;
	}
	
	@PostMapping(value="logout", produces="application/json;charset=utf8")
	public Map<String, Object> logout(HttpServletRequest request) {
	
		Map<String, Object> mapResult = new HashMap<String, Object>();
		HttpSession session = request.getSession(true);
		String id = (String) session.getAttribute("id");
		log.info("logout [" + id + "]");
		
		session.setAttribute("id", null);
		mapResult.put("result", "OK");
		
		log.info("logout [" + id + "] completed.");
		
		return mapResult;
	}
	
	// ------------------------------------------------ Passwordless ------------------------------------------------
	
	// Passwordless login request
	@PostMapping(value="passwordlesslogin", produces="application/json;charset=utf8")
	public Map<String, Object> passwordlesslogin(
			@RequestParam(value = "id", required = false) String id,
			HttpServletRequest request) {
	
		if(id == null)		id = "";
		
		String strScheme = request.getScheme();
		String strServer = request.getServerName();
		int port = request.getServerPort();
		
		String myLoginCallbackUrl = strScheme + "://" + strServer;
		if(port != 80)
			myLoginCallbackUrl += ":" + port;
		myLoginCallbackUrl += passwordlessresult;

		log.info("passwordlesslogin : id [" + id + "] myLoginCallbackUrl [" + myLoginCallbackUrl + "]");

		Map<String, Object> mapResult = new HashMap<String, Object>();

		if(!id.equals("")) {
			
			UserInfo userinfo = new UserInfo();
			userinfo.setId(id);
			UserInfo newUserinfo = loginMapper.getUserInfo(userinfo);
			
			if(newUserinfo == null) {
				String tmp_result = messageUtils.getMessage("text.passwordless.idnotexist");	// ID [" + id + "] does not exist.
				mapResult.put("result", tmp_result.replace("@@@", id));
			}
			else {
				int tokenLifeS = 10;
				String action = "AUTH";
				String authUrl = "/ap/web/simpleapi/p/auth";
				String random = java.util.UUID.randomUUID().toString();
				HttpSession session = request.getSession(true);
				session.setAttribute("_AUTO_RANDOM", random);
				log.info("passwordlesslogin : set random [" + random + "]");

				Algorithm algorithmHS = Algorithm.HMAC512(serverKey);
				String jwt = JWT.create()
					.withExpiresAt(new Date(System.currentTimeMillis()  + tokenLifeS * 1000))
					.withClaim("action", action)
					.withClaim("userId", id)
					.withClaim("random", random)
					.withClaim("corpId", corpId)
					.withClaim("serverId", serverId)
					.withClaim("returnUrl", myLoginCallbackUrl)
					.sign(algorithmHS);

				mapResult.put("result", "OK");
				mapResult.put("jwt", jwt);
				mapResult.put("url", simpleAutopasswordUrl + authUrl);
			}
		}
		else {
			mapResult.put("result", messageUtils.getMessage("text.passwordless.invalid"));	// Invalid id or password.
		}

		return mapResult;
	}
	
	// Passwordless management
	@PostMapping(value="passwordlessmanage", produces="application/json;charset=utf8")
	public Map<String, Object> passwordlessmanage(
			@RequestParam(value = "id", required = false) String id,
			@RequestParam(value = "pw", required = false) String pw,
			HttpServletRequest request) {
	
		if(id == null)	id = "";
		if(pw == null)	pw = "";

		log.info("passwordlessmanage [" + id + "] / ["  + pw + "]");

		Map<String, Object> mapResult = new HashMap<String, Object>();

		if(!id.equals("") && !pw.equals("")) {
			
			UserInfo userinfo = new UserInfo();
			userinfo.setId(id);
			userinfo.setPw(pw);
			UserInfo newUserinfo = loginMapper.checkPassword(userinfo);
			
			if(newUserinfo != null) {
				String strScheme = request.getScheme();
				String strServer = request.getServerName();
				int port = request.getServerPort();
				
				String myManageCallbackUrl = strScheme + "://" + strServer;
				if(port != 80)
					myManageCallbackUrl += ":" + port;
				myManageCallbackUrl += passwordlessresult;

				log.info("passwordlessmanage : id [" + id + "] myManageCallbackUrl [" + myManageCallbackUrl + "]");
				int tokenLifeS = 10;
				String action = "MANAGE";
				String authUrl = "/ap/web/simpleapi/p/manage";
				String random = java.util.UUID.randomUUID().toString();
				HttpSession session = request.getSession(true);
				session.setAttribute("_AUTO_RANDOM", random);
				log.info("passwordlessmanage : set random [" + random + "]");

				Algorithm algorithmHS = Algorithm.HMAC512(serverKey);
				String jwt = JWT.create()
					.withExpiresAt(new Date(System.currentTimeMillis()  + tokenLifeS * 1000))
					.withClaim("action", action)
					.withClaim("userId", id)
					.withClaim("random", random)
					.withClaim("corpId", corpId)
					.withClaim("serverId", serverId)
					.withClaim("returnUrl", myManageCallbackUrl)
					.sign(algorithmHS);

				mapResult.put("result", "OK");
				mapResult.put("jwt", jwt);
				mapResult.put("url", simpleAutopasswordUrl + authUrl);
			}
			else {
				mapResult.put("result", messageUtils.getMessage("text.passwordless.invalid"));	// Invalid id or password.
			}
		}
		else {
			mapResult.put("result", messageUtils.getMessage("text.passwordless.invalid"));	// Invalid id or password.
		}

		return mapResult;
	}
	
	// ----------------------------------------------------------------------------------------------------- Rest API
	
	public String passwordlessCallApi(String userId){
		
		String retVal = "F";

		if(userId == null)	userId = "";
		log.info("passwordlessCallApi : userId=" + userId);
		
		if(userId == null)		userId = "";
		else					userId = userId.strip();
		
		try {
			log.info("URL : " + restCheckUrl + isApUrl + ", userId=" + userId);
			
			String result = callApi("POST", restCheckUrl + isApUrl, "userId=" + userId);
			log.info("passwordlessCallApi : result [" + result + "]");
			
			if(result != null && !result.equals("")) {
				JSONParser parser = new JSONParser();
				try {
					JSONObject jsonResponse = (JSONObject)parser.parse(result);
				    JSONObject jsonData = (JSONObject) jsonResponse.get("data");
				    System.out.println("data=" + jsonData.toString());
				    boolean exist = (boolean) (jsonData).get("exist");
				    
				    if(exist)
				    	retVal = "T";
				    
				} catch(ParseException pe) {
					pe.printStackTrace();
				}
			}
			
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return retVal;
	}
	
	public String callApi(String type, String requestURL, String params) {

 		String retVal = "";
 		Map<String, String> mapParams = getParamsKeyValue(params);

 		try {
			URIBuilder b = new URIBuilder(requestURL);
			
			Set<String> set = mapParams.keySet();
			Iterator<String> keyset = set.iterator();
			while(keyset.hasNext()) {
				String key = keyset.next();
				String value = mapParams.get(key);
				b.addParameter(key, value);
			}
			URI uri = b.build();
		
		    CloseableHttpClient httpClient = HttpClientBuilder.create().build();
		    
		    org.apache.http.HttpResponse response;
		    
		    if(type.toUpperCase().equals("POST")) {
		        HttpPost httpPost = new HttpPost(uri);
		        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
		    	response = httpClient.execute(httpPost);
		    }
		    else {
		    	HttpGet httpGet = new HttpGet(uri);
		    	httpGet.addHeader("Content-Type", "application/x-www-form-urlencoded");
		    	response = httpClient.execute(httpGet);
		    }
		    
		    HttpEntity entity = response.getEntity();
		    retVal = EntityUtils.toString(entity);
		} catch(Exception e) {
			System.out.println(e.toString());
		}
		
		return retVal;
 	}
	
	public Map<String, String> getParamsKeyValue(String params) {
 		String[] arrParams = params.split("&");
         Map<String, String> map = new HashMap<String, String>();
         for (String param : arrParams)
         {
         	String name = "";
         	String value = "";
         	
         	String[] tmpArr = param.split("=");
             name = tmpArr[0];
             

             if(tmpArr.length == 2)
             	value = tmpArr[1];
             	
             map.put(name, value);
         }

         return map;
 	}
}
