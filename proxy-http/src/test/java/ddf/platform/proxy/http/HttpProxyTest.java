/**
 * Copyright (c) Codice Foundation
 * 
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 * 
 **/

package ddf.platform.proxy.http;

import java.util.List;

import org.apache.camel.component.mock.MockComponent;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HttpProxyTest extends CamelTestSupport {
	 private static final transient Logger LOGGER = LoggerFactory
	            .getLogger(HttpProxyTest.class);

	    private ModelCamelContext camelContext;

	    private HttpProxyBeanImpl httpProxyImpl;

	    @After
	    public void tearDown() throws Exception {
	        LOGGER.debug("INSIDE tearDown");
	        //context = null;
	        
	        // This will also stop all routes/components/endpoints, etc. 
	        // and clear internal state/cache
	        camelContext.stop();
	        camelContext = null;
	    }

	    @Test
	    public void testRouteCreationWithNoQueryString() throws Exception {
	        String proxyUri = "http://0.0.0.0:8181/test";
	        String targetUri = "http://time.is";

	        RouteDefinition routeDefinition = createRoute(proxyUri, targetUri);

	        verifyRoute(routeDefinition, proxyUri, targetUri);
	    }

	    @Test
	    public void testRouteCreationWithQueryString() throws Exception {
	        String proxyUri = "http://0.0.0.0:8181/test?hello=world";
	        String targetUri = "http://time.is?foo=bar";

	        RouteDefinition routeDefinition = createRoute(proxyUri, targetUri);

	        verifyRoute(routeDefinition, proxyUri, targetUri);
	    }

	    @Test
	    public void testRouteCreationMissingProxyUri() throws Exception {
	        String proxyUri = "";
	        String targetUri = "http://time.is?foo=bar";

	        camelContext = (ModelCamelContext) super.createCamelContext();
	        camelContext.start();

	        // Map the "content" scheme to a mock component so that we do not have to
	        // mock the entire custom ContentComponent and include its implementation
	        // in pom with scope=test
	        camelContext.addComponent("proxy-http", new MockComponent());

	        httpProxyImpl = new HttpProxyBeanImpl(camelContext);
	        httpProxyImpl.setProxyUri(proxyUri);
	        httpProxyImpl.setTargetUri(targetUri);

	        // Simulates what container would do once all setters have been invoked
	        httpProxyImpl.init();

	        assertEquals(camelContext.getRouteDefinitions().size(), 0);
	    }

	    @Test
	    public void testRouteCreationMissingTargetUri() throws Exception {
	    	String proxyUri = "http://0.0.0.0:8181/test?hello=world";
	        String targetUri = "";

	        camelContext = (ModelCamelContext) super.createCamelContext();
	        camelContext.start();

	        // Map the "content" scheme to a mock component so that we do not have to
	        // mock the entire custom ContentComponent and include its implementation
	        // in pom with scope=test
	        camelContext.addComponent("proxy-http", new MockComponent());

	        httpProxyImpl = new HttpProxyBeanImpl(camelContext);
	        httpProxyImpl.setProxyUri(proxyUri);
	        httpProxyImpl.setTargetUri(targetUri);

	        // Simulates what container would do once all setters have been invoked
	        httpProxyImpl.init();

	        assertEquals(camelContext.getRouteDefinitions().size(), 0);
	    }

	 

	    private RouteDefinition createRoute(String proxyUri, String targetUri) throws Exception {
	        
	        // Simulates what container would do for <camel:camelContext id="camelContext">
	        // declaration in beans.xml file
	        camelContext = (ModelCamelContext) super.createCamelContext();
	        camelContext.start();

	        // Map the "content" scheme to a mock component so that we do not have to
	        // mock the entire custom ContentComponent and include its implementation
	        // in pom with scope=test
	        camelContext.addComponent("proxy-http", new MockComponent());

	        httpProxyImpl = new HttpProxyBeanImpl(camelContext);
	        httpProxyImpl.setProxyUri(proxyUri);
	        httpProxyImpl.setTargetUri(targetUri);

	        // Simulates what container would do once all setters have been invoked
	        httpProxyImpl.init();

	        // Initial Camel route should now be created
	        List<RouteDefinition> routeDefinitions = httpProxyImpl.getRouteDefinitions();
	        assertEquals(routeDefinitions.size(), 1);
	        LOGGER.debug("routeDefinition = " + routeDefinitions.get(0).toString());

	        return routeDefinitions.get(0);
	    }

	    private void verifyRoute(RouteDefinition routeDefinition, String proxyUri,
	            String targetUri) {
	        List<FromDefinition> fromDefinitions = routeDefinition.getInputs();
	        assertEquals(fromDefinitions.size(), 1);
	        String returnedFromUri = fromDefinitions.get(0).getUri();
	        LOGGER.debug("returnedFromUri = " + returnedFromUri);
	        
        	String fromUri = "";
        	String toUri = "";
	        if(proxyUri.contains("?")){
            	fromUri = "jetty:" + proxyUri + "&matchOnUriPrefix=false&continuationTimeout=0";

        	} else {
        		fromUri = "jetty:" + proxyUri + "?matchOnUriPrefix=false&continuationTimeout=0";
        	}
        	
	        assertEquals(returnedFromUri, fromUri);
	        List<ProcessorDefinition<?>> processorDefinitions = routeDefinition.getOutputs();

	        // expect 4 outputs: SetHeader(operation), SetHeader(directive), SetHeader(contentUri),
	        // To(content:framework)
	        assertEquals(processorDefinitions.size(), 1);
	    }
}
