/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.frameworkset.elasticsearch.client;

import com.frameworkset.util.SimpleStringUtil;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.ResponseHandler;
import org.apache.http.conn.HttpHostConnectException;
import org.frameworkset.elasticsearch.*;
import org.frameworkset.spi.remote.http.HttpRequestUtil;
import org.frameworkset.spi.remote.http.StringResponseHandler;
import org.frameworkset.util.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.*;

//import org.apache.http.client.HttpClient;
//import org.apache.http.impl.client.DefaultHttpClient;

/**
 * Rest ElasticSearch client which is responsible for sending bulks of events to
 * ElasticSearch using ElasticSearch HTTP API. This is configurable, so any
 * config params required should be taken through this.
 */
public class ElasticSearchRestClient implements ElasticSearchClient {

	public static final String INDEX_OPERATION_NAME = "index";
	public static final String INDEX_PARAM = "_index";
	public static final String TYPE_PARAM = "_type";
	public static final String TTL_PARAM = "_ttl";
	public static final String BULK_ENDPOINT = "_bulk";
	private static final Logger logger = LoggerFactory.getLogger(ElasticSearchRestClient.class);
	protected final RoundRobinList serversList;
	protected Properties extendElasticsearchPropes;
	protected String httpPool;
	protected String elasticUser;
	protected String elasticPassword;
	protected long healthCheckInterval = -1l;
//	private HttpClient httpClient;
	protected Map<String, String> headers = new HashMap<>();
	protected boolean showTemplate = false;
	protected List<ESAddress> addressList;
 
	protected FastDateFormat fastDateFormat = FastDateFormat.getInstance("yyyy.MM.dd",
      TimeZone.getTimeZone("Etc/UTC"));
    
	protected String dateFormat = "yyyy.MM.dd";

	protected TimeZone timeZone = TimeZone.getTimeZone("Etc/UTC");
	protected ElasticSearch elasticSearch;
	public ElasticSearchRestClient(ElasticSearch elasticSearch,String[] hostNames, String elasticUser, String elasticPassword,
								     Properties extendElasticsearchPropes) {
		this.extendElasticsearchPropes = extendElasticsearchPropes;
		this.elasticSearch = elasticSearch;
		addressList = new ArrayList<ESAddress>();
		for(String host:hostNames){
			addressList.add(new ESAddress(host));
		}
		serversList = new RoundRobinList(addressList);


//		httpClient = new DefaultHttpClient();
		this.elasticUser = elasticUser;
		this.elasticPassword = elasticPassword;
		this.init();
	}
	private boolean containAddress(ESAddress address){
		ESAddress temp = null;
		for (int i = 0; i < addressList.size(); i ++){
			temp = addressList.get(i);
			if(temp.equals(address)){
				return true;
			}
		}
		return false;
	}
	public void addAddress(String[] address){
		List<ESAddress> temp = new ArrayList<ESAddress>();
		for(String host:address){
			ESAddress esAddress = new ESAddress(host);
			if(!containAddress(esAddress))
				temp.add(new ESAddress(host));
		}
		if(temp.size()> 0)
			this.serversList.addAddress(temp);
	}


//	@VisibleForTesting
//	public ElasticSearchRestClient(String[] hostNames, String elasticUser, String elasticPassword,
//								   ElasticSearchEventSerializer serializer, Properties extendElasticsearchPropes) {
//		this(hostNames, elasticUser, elasticPassword, serializer, extendElasticsearchPropes);
//
//	}

	public void init() {
		//Authorization
		if (elasticUser != null && !elasticUser.equals(""))
			headers.put("Authorization", getHeader(elasticUser, elasticPassword));
		if(healthCheckInterval > 0) {
			logger.info("start elastic healthCheck thread,you can set elasticsearch.healthCheckInterval=-1 in "+this.elasticSearch.getApplicationContext().getConfigfile()+" to disable healthCheck thread.");
			HealthCheck healthCheck = new HealthCheck(addressList, healthCheckInterval,headers);
			healthCheck.run();
		}

	}

	public static String getHeader(String user, String password) {
		String auth = user + ":" + password;
		byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("US-ASCII")));
		return "Basic " + new String(encodedAuth);
	}

	@Override
	public void configure(Properties elasticsearchPropes) {
		String dateFormatString = elasticsearchPropes.getProperty(TimeBasedIndexNameBuilder.DATE_FORMAT);

	    String timeZoneString = elasticsearchPropes.getProperty(TimeBasedIndexNameBuilder.TIME_ZONE);
	    
	    String showTemplate_ = elasticsearchPropes.getProperty("elasticsearch.showTemplate");
	    String httpPool = elasticsearchPropes.getProperty("elasticsearch.httpPool");
	    if(httpPool == null || httpPool.equals("")){
			httpPool = "default";
		}
	    if(showTemplate_ != null && showTemplate_.equals("true")){
	    	this.showTemplate = true;
	    }
	    if (SimpleStringUtil.isEmpty(dateFormatString)) {
	      dateFormatString = TimeBasedIndexNameBuilder.DEFAULT_DATE_FORMAT;
	    }
	    if (SimpleStringUtil.isEmpty(timeZoneString)) {
	      timeZoneString = TimeBasedIndexNameBuilder.DEFAULT_TIME_ZONE;
	    }
	    this.dateFormat = dateFormatString;
	    this.timeZone = TimeZone.getTimeZone(timeZoneString);
	    fastDateFormat = FastDateFormat.getInstance(dateFormatString,
	        TimeZone.getTimeZone(timeZoneString));
	    String healthCheckInterval_ = elasticsearchPropes.getProperty("elasticsearch.healthCheckInterval");
		if(healthCheckInterval_ == null){
			this.healthCheckInterval = 3000l;
		}
		else{
			try {
				this.healthCheckInterval = Long.parseLong(healthCheckInterval_);
			}
			catch (Exception e){
				logger.error("Parse Long healthCheckInterval parameter failed:"+healthCheckInterval_,e);
			}
		}

	}

	@Override
	public void close() {
	}

	

//  private void initHttpRequest(HttpPost httpRequest){
//    if (headers != null && headers.size() > 0) {
//      Iterator<Map.Entry<String, String>> entries = headers.entrySet().iterator();
//      while (entries.hasNext()) {
//        Map.Entry<String, String> entry = entries.next();
//        httpRequest.addHeader(entry.getKey(), entry.getValue());
//      }
//    }
//  }

	public String execute(String entity) throws ElasticSearchException {
		int triesCount = 0;
		String response = null;
		Throwable e = null;
		while (true) {

			ESAddress host = serversList.get();
			String url = host.getAddress() + "/" + BULK_ENDPOINT;
			try {
				if(this.showTemplate && logger.isInfoEnabled()){
					logger.info(entity);
				}
				response = HttpRequestUtil.sendJsonBody(httpPool,entity, url, this.headers);
//				if (response != null) {
//
//					logger.info("Status message from elasticsearch: " + response);
//
//				}
				break;
			} 
			catch (HttpHostConnectException ex) {
				host.setStatus(1);
				if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                
            } catch (UnknownHostException ex) {
            	host.setStatus(1);
            	if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                 
            }
			catch (IOException ex) {
				host.setStatus(1);
				if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                
            }
			catch (ElasticSearchException ex) {
				e = ex;
				break;
			}
			catch (Exception ex) {
				e = ex;
				break;
			}
			catch (Throwable ex) {
				e = ex;
				break;
			}



		}
		if (e != null){
			if(e instanceof ElasticSearchException)
				throw (ElasticSearchException)e;
			throw new ElasticSearchException(e);
		}
		return response;


//    if (statusCode != HttpStatus.SC_OK) {
//      if (response.getEntity() != null) {
//        throw new EventDeliveryException(EntityUtils.toString(response.getEntity(), "UTF-8"));
//      } else {
//        throw new EventDeliveryException("Elasticsearch status code was: " + statusCode);
//      }
//    }
	}

	@Override
	public ClientUtil getClientUtil(IndexNameBuilder indexNameBuilder) {
		// TODO Auto-generated method stub
		return new RestClientUtil(this, indexNameBuilder);
	}

	@Override
	public ClientUtil getConfigClientUtil(IndexNameBuilder indexNameBuilder,String configFile) {
		// TODO Auto-generated method stub
		return new ConfigRestClientUtil(this, indexNameBuilder,configFile);
	}
	public String executeHttp(String path,String action) throws ElasticSearchException{
		return executeHttp(path, null,  action) ;
	}

	public <T> T executeHttp(String path,String action,ResponseHandler<T> responseHandler) throws ElasticSearchException{
		return executeHttp(path, null,  action, responseHandler) ;
	}

	private String getPath(String host,String path){
		String url = path.equals("") || path.startsWith("/")?
				new StringBuilder().append(host).append(path).toString()
				:new StringBuilder().append(host).append("/").append(path).toString();
		return url;
	}
	/**
	 * 
	 * @param path
	 * @param entity
	 * @param action get,post,put,delete
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T executeHttp(String path, String entity,String action,ResponseHandler<T> responseHandler) throws ElasticSearchException {
		int triesCount = 0;
		T response = null;
		Throwable e = null;
		if(this.showTemplate && logger.isInfoEnabled()){
			logger.info("Elastic search action:{},request body:{}",path,entity);
		}
		while (true) {

			ESAddress host = serversList.get();
			String url = getPath(host.getAddress(),path);
//			path.equals("") || path.startsWith("/")?
//					new StringBuilder().append(host.getAddress()).append(path).toString()
//					:new StringBuilder().append(host.getAddress()).append("/").append(path).toString();
			try {
				if (entity == null){
					if(action == null)				
						response = HttpRequestUtil.httpPostforString(httpPool,url, null, this.headers, responseHandler);
					else if(action == ClientUtil.HTTP_POST )				
						response = HttpRequestUtil.httpPostforString(httpPool,url, null, this.headers,responseHandler);
					else if( action == ClientUtil.HTTP_PUT)				
						response = HttpRequestUtil.httpPutforString(httpPool,url, null, this.headers,responseHandler);
					else if(action == ClientUtil.HTTP_GET)				
						response = HttpRequestUtil.httpGetforString(httpPool,url, this.headers,responseHandler);
					else if(action == ClientUtil.HTTP_DELETE)				
						response = HttpRequestUtil.httpDelete(httpPool,url, null, this.headers,responseHandler);
					else
						throw new java.lang.IllegalArgumentException("not support http action:"+action);
				}
				else
				{
					 if(action == ClientUtil.HTTP_POST )	
						 response = HttpRequestUtil.sendJsonBody(httpPool,entity, url, this.headers,responseHandler);
					 else if( action == ClientUtil.HTTP_PUT)	
					 {
						 response = HttpRequestUtil.putJson(httpPool,entity, url, this.headers,responseHandler);
					 }
					else
						throw new java.lang.IllegalArgumentException("not support http action:"+action);
				}
//				if (response != null) {
//
//					logger.info("Status message from elasticsearch: " + response);
//
//				}
				break;
			} catch (HttpHostConnectException ex) {
				host.setStatus(1);
				if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                
            } catch (UnknownHostException ex) {
            	host.setStatus(1);
            	if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                 
            }
			catch (IOException ex) {
				host.setStatus(1);
				if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                
            }
			catch (ElasticSearchException ex) {
				e = ex;
				break;
			}
			catch (Exception ex) {
				e = ex;
				break;
			}
			catch (Throwable ex) {
				e = ex;
				break;
			}



		}
		if (e != null){
			if(e instanceof ElasticSearchException)
				throw (ElasticSearchException)e;
			throw new ElasticSearchException(e);
		}
		return response;
	}

	/**
	 *
	 * @param path
	 * @param entity
	 * @param action get,post,put,delete
	 * @return
	 * @throws ElasticSearchException
	 */
	public String executeHttp(String path, String entity,String action) throws ElasticSearchException {
		return executeHttp( path,  entity, action,new StringResponseHandler());
	}

	public String executeRequest(String path, String entity) throws ElasticSearchException {
		int triesCount = 0;
		String response = null;
		Throwable e = null;
		if(this.showTemplate && logger.isInfoEnabled()){
			logger.info("Elastic search action:{},request body:\n{}",path,entity);
		}
		while (true) {

			ESAddress host = serversList.get();
			String url =  getPath(host.getAddress(),path);
//					new StringBuilder().append(host.getAddress()).append("/").append(path).toString();
			try {
				if (entity == null)
					response = HttpRequestUtil.httpPostforString(url, null, this.headers);
				else
					response = HttpRequestUtil.sendJsonBody(entity, url, this.headers);
//				if (response != null) {
//					if(logger.isDebugEnabled())
//						logger.debug("Status message from elasticsearch: {}", response);
//
//				}
				break;
			} 
			
			catch (HttpHostConnectException ex) {
				host.setStatus(1);
				if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                
            } catch (UnknownHostException ex) {
            	host.setStatus(1);
            	if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                 
            }
			catch (IOException ex) {
				host.setStatus(1);
				if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                
            }
			catch (ElasticSearchException ex) {
				throw ex;
			}
		
			catch (Exception ex) {
				e = ex;
				break;
			}
			catch (Throwable ex) {
				e = ex;
				break;
			}



		}
		if (e != null){
			
			throw new ElasticSearchException(e);
		}
		return response;
	}
	public <T> T executeRequest(String path, String entity,ResponseHandler<T> responseHandler) throws ElasticSearchException{
		return executeRequest(path, entity,responseHandler,ClientUtil.HTTP_POST);
	}
	/**
	 * 需要补充容错机制
	 * @param path
	 * @param entity
	 * @param responseHandler
	 * @return
	 * @throws ElasticSearchException
	 */
	public <T> T executeRequest(String path, String entity,ResponseHandler<T> responseHandler,String action) throws ElasticSearchException {
		T response = null;
		int triesCount = 0;
		Throwable e = null;
		if(this.showTemplate && logger.isInfoEnabled()){
			logger.info("Elastic search action:{},request body:\n{}",path,entity);
		}
		while (true) {

			ESAddress host = serversList.get();
//			String url = new StringBuilder().append(host.getAddress()).append("/").append(path).toString();
			String url =  getPath(host.getAddress(),path);
			try {
//				if (entity == null)
//					response = HttpRequestUtil.httpPostforString(url, null, this.headers,  responseHandler);
//				else
//					response = HttpRequestUtil.sendJsonBody(entity, url, this.headers, responseHandler);

				if (entity == null){
					if(action == null)
						response = HttpRequestUtil.httpPostforString(httpPool,url, null, this.headers,  responseHandler);
					else if(action == ClientUtil.HTTP_POST )
						response = HttpRequestUtil.httpPostforString(httpPool,url, null, this.headers,  responseHandler);
					else if( action == ClientUtil.HTTP_PUT)
						response = HttpRequestUtil.httpPutforString(httpPool,url, null, this.headers,  responseHandler);
					else if(action == ClientUtil.HTTP_GET)
						response = HttpRequestUtil.httpGetforString(httpPool,url, this.headers,  responseHandler);
					else if(action == ClientUtil.HTTP_DELETE)
						response = HttpRequestUtil.httpDelete(httpPool,url, null, this.headers,  responseHandler);
					else
						throw new java.lang.IllegalArgumentException("not support http action:"+action);
				}
				else
				{
					if(action == ClientUtil.HTTP_POST )
						response = HttpRequestUtil.sendJsonBody(httpPool,entity, url, this.headers,  responseHandler);
					else if( action == ClientUtil.HTTP_PUT)
					{
						response = HttpRequestUtil.putJson(httpPool,entity, url, this.headers,  responseHandler);
					}
					else if(action == ClientUtil.HTTP_DELETE)
						response = HttpRequestUtil.httpDelete(httpPool,url, null, this.headers,  responseHandler);
					else
						throw new java.lang.IllegalArgumentException("not support http action:"+action);

				}
				break;
			} catch (HttpHostConnectException ex) {
				host.setStatus(1);
				if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                
            } catch (UnknownHostException ex) {
            	host.setStatus(1);
            	if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                 
            }
			catch (IOException ex) {
				host.setStatus(1);
				if (triesCount < serversList.size()) {//失败尝试下一个地址
					triesCount++;
					continue;
				} else {
					e = ex;
					break;
				}
                
            }
			catch (ElasticSearchException ex) {
				throw ex;
			}

			catch (Exception ex) {
				e = ex;
				break;
			}
			catch (Throwable ex) {
				e = ex;
				break;
			}
//			throw new ElasticSearchException(e);

		}
		if (e != null){
			if(e instanceof ElasticSearchException)
				throw (ElasticSearchException)e;
			throw new ElasticSearchException(e);
		}
		return response;
	}

	public FastDateFormat getFastDateFormat() {
		return fastDateFormat;
	}
	 

	public String getDateFormat() {
		return dateFormat;
	}

	public void setDateFormat(String dateFormat) {
		this.dateFormat = dateFormat;
	}

	public TimeZone getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(TimeZone timeZone) {
		this.timeZone = timeZone;
	}

	public boolean isShowTemplate() {
		return showTemplate;
	}

	public void setShowTemplate(boolean showTemplate) {
		this.showTemplate = showTemplate;
	}
	
}
