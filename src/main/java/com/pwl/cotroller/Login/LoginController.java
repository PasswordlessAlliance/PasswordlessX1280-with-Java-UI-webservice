package com.pwl.cotroller.Login;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.pwl.domain.Login.UserInfo;
import com.pwl.mapper.Login.LoginMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class LoginController {
	
	@Autowired
	public LoginMapper loginMapper;
	
	@Value("${passwordless.simpleCheckUrl}")
	private String simpleCheckUrl;
	
	@Value("${passwordless.serverKey}")
	private String serverKey;
	
	// Management Types
	private int REQ_DEL = 1;  			// Delete
	private int REQ_REG = 2;			// Register
	private int REQ_AUTH = 3;			// Authenticate
	
	// Management Results
	private int RES_SUCCESS = 1;		// Success
	private int RES_FAIL = 0;			// Failure
	
	// Login
	@RequestMapping(value="/Login/login.do")
	public String login(Model model, HttpServletRequest request) {

		return "/Login/login";
	}
	
	// Sign Up
	@RequestMapping(value="/Login/join.do")
	public String join(Model model, HttpServletRequest request) {
		
		return "/Login/join";
	}
	
	// Change Password
	@RequestMapping(value="/Login/changepw.do")
	public String changepw(Model model, HttpServletRequest request) {
		
		return "/Login/changepw";
	}

	// Logout
	@RequestMapping(value="/Login/Logout.do")
	public String Logout(Model model, HttpServletRequest request) {
		
		HttpSession session = request.getSession(true);
		session.setAttribute("id", "");
		
		return "/Login/logout";
	}
	
	// Passwordless Request Result
	@RequestMapping("/Login/passwordlessresult.do")
	public String passwordlessresult(HttpServletRequest request, HttpServletResponse response, Model model) {

		String sessionRandom = "";
		String accessToken = request.getParameter("accessToken");
		if(accessToken == null)
			accessToken = "";

		HttpSession session = request.getSession(false);
		if(session != null) {
			sessionRandom = (String) session.getAttribute("_AUTO_RANDOM");
			if(sessionRandom == null)
				sessionRandom = "";
			
			log.info("passwordlessresult : del random [" + sessionRandom + "]");
			session.removeAttribute("_AUTO_RANDOM");
		}

		log.info("passwordlessresult : accessToken [" + accessToken + "]");
		log.info("passwordlessresult : random      [" + sessionRandom + "]");
		
		String strResult = "";
		String params = "";
		
		if(!sessionRandom.equals("") && !accessToken.equals("")) {
			params = "?accessToken=" + accessToken + "&random=" + sessionRandom;
			log.info("passwordlessresult : sendGet [" + simpleCheckUrl + params + "]");
			try {
				strResult = sendGet(simpleCheckUrl + params);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		log.info("passwordlessresult : result [" + strResult + "]");
		
		String code = "";
		String resultToken = "";
		
		if(!strResult.equals("")) {
			
			JSONParser parser = new JSONParser();
			try {
				JSONObject jsonResponse = (JSONObject)parser.parse(strResult);
			    code = (String) jsonResponse.get("code");
			    JSONObject jsonData = (JSONObject) jsonResponse.get("data");
			    resultToken = (String) (jsonData).get("resultToken");
			} catch(ParseException pe) {
				pe.printStackTrace();
			}
			log.info("code        [" + code + "]");
			log.info("resultToken [" + resultToken + "]");
	
			Algorithm algorithm = Algorithm.HMAC512(serverKey);
			JWTVerifier verifier = JWT.require(algorithm)
				.acceptExpiresAt(10)
				.build(); // Reusable verifier instance
			
			DecodedJWT jwt = null;
			
			int result = -1;
			String action = "";
			int subAction = -1;
			String random = "";
			String userId = "";
	
			try {
				jwt = verifier.verify(resultToken);
				random = jwt.getClaim("random").asString();
				action = jwt.getClaim("action").asString();
				subAction = jwt.getClaim("subAction").asInt();	// 1: Delete, 2: Register, 3: Authenticate
				result = jwt.getClaim("result").asInt();		// 1: Success, 0: Failure
				userId = jwt.getClaim("userId").asString();
				
				log.info("result      [" + result + "]");
				log.info("random      [" + random + "]");
				log.info("action      [" + action + "]");
				log.info("subAction   [" + subAction + "]");
				log.info("userId      [" + userId + "]");
				
				if(action.equals("AUTH")) {
					// If the action type is REQ_AUTH(3)
					if(subAction == REQ_AUTH) {
						
						// If the result is RES_SUCCESS(1), handle successful login
						if(result == RES_SUCCESS) {
							log.info("Login verification OK - Change password");
							
							// Change password upon successful login
							String newPw = Long.toString(System.currentTimeMillis()) + ":" + userId;
							UserInfo userinfo = new UserInfo();
							userinfo.setId(userId);
							userinfo.setPw(newPw);
							loginMapper.changepw(userinfo);
							
							session.setAttribute("id", userId);
							model.addAttribute("result", "OK");
						}
						else {
							log.info("Login verification FAIL");
							
							session.setAttribute("id", "");
							model.addAttribute("result", "Login failed");
						}
					}
				}
				else if(action.equals("MANAGE")) {
					// If the action type is REQ_DEL(1) or REQ_REG(2)
					if(subAction == REQ_DEL || subAction == REQ_REG) {
						
						// If the result is RES_SUCCESS(1), handle success
						if(result == RES_SUCCESS) {
							log.info("Management request OK");
							
							// If QR is registered, change the password
							if(subAction == REQ_REG) {
								log.info("QR registration successful - Change password");
								String newPw = Long.toString(System.currentTimeMillis()) + ":" + userId;
								UserInfo userinfo = new UserInfo();
								userinfo.setId(userId);
								userinfo.setPw(newPw);
								loginMapper.changepw(userinfo);
							}
							
							model.addAttribute("result", "OK");
						}
						else if(result == RES_FAIL){
							log.info("Management request FAIL");
							
							model.addAttribute("result", "Login failed");
						}
					}
				}
				else {
					model.addAttribute("result", "Unknown request");
				}
				
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		else {
			model.addAttribute("result", "Server did not respond.");
		}
		
		return "/Login/passwordlessresult";
	}
	
	// ------------------------------------------------ UTILS ------------------------------------------------
	
	public String sendGet(String url) throws Exception {
		
		final String USER_AGENT = "Mozilla/5.0";
		
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();

		// Optional default is GET
		con.setRequestMethod("GET");

		// Add request header
		con.setRequestProperty("User-Agent", USER_AGENT);

		int responseCode = con.getResponseCode();
		
		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();

		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		return response.toString();
	}
}
