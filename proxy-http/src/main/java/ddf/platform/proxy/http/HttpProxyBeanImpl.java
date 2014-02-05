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
import java.util.Map;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpProxyBeanImpl implements HttpProxyBean{
	 private String proxyUri = null;
	 private String targetUri = null;
	 private ModelCamelContext camelContext;
	 private List<RouteDefinition> routeCollection;
	 private static final Logger LOGGER = LoggerFactory.getLogger(HttpProxyBeanImpl.class);
	
	/**
     * Constructs a Http Proxy to accept requests locally and proxy out to
     * an external server.
     * 
     * @param camelContext the Camel context to use across all Http Proxies.
     *  Note that if Apache changes this ModelCamelContext interface there
     * is no guarantee that whatever DM is being used (Spring in this case) will be
     * updated accordingly.
     */
    public HttpProxyBeanImpl(final ModelCamelContext camelContext) {
        this.camelContext = camelContext;
        LOGGER.trace("Http Proxy(CamelContext) constructor done");
    }
    
    /**
     * This method will stop and remove any existing Camel routes in this context, and then
     * configure a new Camel route using the properties set in the setter methods.
     * 
     * Invoked after all of the setter methods have been called (for initial route creation), and
     * also called whenever an existing route is updated.
     */
    public void init() {
        LOGGER.trace("INSIDE: init()");

        if (routeCollection != null) {
            try {
                // This stops the route before trying to remove it
                LOGGER.debug("Removing " + routeCollection.size() + " routes");
                camelContext.removeRouteDefinitions(routeCollection);
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        } else {
            LOGGER.debug("No routes to remove before configuring a new route");
        }

        configureCamelRoute();
    }

    /**
     * Remove all of the camel routes for Http Proxies
     */
    public void destroy() {
        LOGGER.trace("INSIDE: destroy()");        
        removeRoutes();
    }
    
    /**
     * Invoked when updates are made to the configuration of existing Http Proxies. This
     * method is invoked by the container as specified by the update-strategy and update-method
     * attributes in Spring beans XML file.
     * 
     * @param properties
     */
    public void updateCallback(Map<String, Object> properties) {
        LOGGER.trace("ENTERING: updateCallback");

        if (properties != null) {
            setProxyUri((String) properties.get("proxyUri"));
            setTargetUri((String) properties.get("targetUri"));
            init();
        }

        LOGGER.trace("EXITING: updateCallback");
    }
    
    public List<RouteDefinition> getRouteDefinitions() {
        return camelContext.getRouteDefinitions();
    }
    
    public void setProxyUri(String proxyUri){
    	this.proxyUri = proxyUri;
    }
    
    public void setTargetUri(String targetUri){
    	this.targetUri = targetUri;
    }
    
    /**
     * Configures the Camel routes for the Http Proxies
     */
    private void configureCamelRoute() {
        LOGGER.trace("ENTERING: configureCamelRoute");

        // Must have a http proxy URI.
        if (StringUtils.isEmpty(proxyUri)) {
            LOGGER.debug("Cannot setup camel route - must specify an Http Proxy URI");
            return;
        }
        // Must have a target URI.
        if (StringUtils.isEmpty(targetUri)) {
            LOGGER.debug("Cannot setup camel route - must specify a target URI");
            return;
        }

        RouteBuilder routeBuilder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
            	String fromUri = null;
            	String toUri = null;
            	//Remove ending slash if exists
            	if (("/").equals(proxyUri.substring(proxyUri.length() - 1))){
            		proxyUri = proxyUri.substring(0, proxyUri.length() - 1);
            	}
            	if (("/").equals(targetUri.substring(targetUri.length() - 1))){
            		targetUri = targetUri.substring(0, targetUri.length() - 1);
            	}
            	
            	//handle if url already has query string; need to add on camel options elegantly
            	
            	if(proxyUri.contains("?")){
                	fromUri = "jetty:" + proxyUri + "&matchOnUriPrefix=false&continuationTimeout=0";

            	} else {
            		fromUri = "jetty:" + proxyUri + "?matchOnUriPrefix=false&continuationTimeout=0";
            	}
            	
            	if(targetUri.contains("?")){
                	toUri = "jetty:" + targetUri + "&bridgeEndpoint=true&amp;throwExceptionOnFailure=false";

            	} else {
            		toUri = "jetty:" + targetUri + "?bridgeEndpoint=true&amp;throwExceptionOnFailure=false";
            	}
                LOGGER.debug("fromHttpProxyUri = " + fromUri);
                LOGGER.debug("toTargetUri = " + toUri);
                from(fromUri).to(toUri);
            }
        };

        try {
            // Add the routes that will be built by the RouteBuilder class above
            // to this CamelContext.
            // The addRoutes() method will instantiate the RouteBuilder class above,
            // and start the routes (only) if the camelContext has already been started.
            camelContext.addRoutes(routeBuilder);

            // Save the routes created by RouteBuilder so that they can be
            // stopped and removed later if the route(s) are modified by the
            // administrator or this HttpProxyImpl is deleted.
            this.routeCollection = routeBuilder.getRouteCollection().getRoutes();

            // Start route that was just added.
            // If the route was just added for the first time, i.e., this not a bundle
            // restart, then this method will do nothing since the addRoutes() above
            // already started the route. But for bundle (or system) restart this call
            // is needed since the addRoutes() for whatever reason did not start the route.
            startRoutes();
            
            if (LOGGER.isDebugEnabled()) {
                dumpCamelContext("after configureCamelRoute()");
            }
        } catch (Exception e) {
            LOGGER.error("Unable to configure Camel route - this Http Proxy will be unusable", e);
        }

        LOGGER.trace("EXITING: configureCamelRoute");
    }
    
    private void startRoutes() {
        LOGGER.trace("ENTERING: startRoutes");
        List<RouteDefinition> routeDefinitions = camelContext.getRouteDefinitions();
        for (RouteDefinition routeDef : routeDefinitions) {
            startRoute(routeDef);
        }
        LOGGER.trace("EXITING: startRoutes");
    }
    
    private void startRoute(RouteDefinition routeDef) {
        String routeId = routeDef.getId();
        try {
            if (isMyRoute(routeId)) {
                ServiceStatus routeStatus = camelContext.getRouteStatus(routeId);
                // Only start the route if it is not already started
                if (routeStatus == null || !routeStatus.isStarted()) {
                    LOGGER.trace("Starting route with ID = " + routeId);
                    //camelContext.startRoute(routeDef);  //DEPRECATED
                    // this method does not reliably start a route that was created, then
                    // app shutdown, and restarted
                    camelContext.startRoute(routeId);  
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Unable to start Camel route with route ID = " + routeId, e);
        }
    }
    
    private boolean isMyRoute(String routeId) {
        
        boolean status = false;
        
        if (this.routeCollection != null) {
            for (RouteDefinition routeDef : this.routeCollection) {
                if (routeDef.getId().equals(routeId)) {
                    return true;
                }
            }
        }
        
        return status;
    }

    private void removeRoutes() {
        LOGGER.trace("ENTERING: stopRoutes");
        List<RouteDefinition> routeDefinitions = camelContext.getRouteDefinitions();
        for (RouteDefinition routeDef : routeDefinitions) {
            try {
                // Only remove routes that this Http Proxy created
                // (since same camelContext shared across all Http Proxies
                // this is necessary)
                if (isMyRoute(routeDef.getId())) {
                    LOGGER.trace("Stopping route with ID = " + routeDef.getId());
                    //camelContext.stopRoute(routeDef);  //DEPRECATED
                    camelContext.stopRoute(routeDef.getId());
                    boolean status = camelContext.removeRoute(routeDef.getId());
                    LOGGER.trace("Status of removing route " + routeDef.getId() + " is " + status);
                    camelContext.removeRouteDefinition(routeDef);
                }
            } catch (Exception e) {
                LOGGER.warn("Unable to stop Camel route with route ID = " + routeDef.getId(), e);
            }
        }

        LOGGER.trace("EXITING: stopRoutes");
    }
    
    private void dumpCamelContext(String msg) {
        LOGGER.debug("\n\n***************  START: " + msg + "  *****************");
        List<RouteDefinition> routeDefinitions = camelContext.getRouteDefinitions();
        if (routeDefinitions != null) {
            LOGGER.debug("Number of routes = " + routeDefinitions.size());
            for (RouteDefinition routeDef : routeDefinitions) {
                String routeId = routeDef.getId();
                LOGGER.debug("route ID = " + routeId);
                List<FromDefinition> routeInputs = routeDef.getInputs();
                if (routeInputs.isEmpty()) {
                    LOGGER.debug("routeInputs are EMPTY");
                } else {
                    for (FromDefinition fromDef : routeInputs) {
                        LOGGER.debug("route input's URI = " + fromDef.getUri());
                    }
                }
                ServiceStatus routeStatus = camelContext.getRouteStatus(routeId);
                if (routeStatus != null) {
                    LOGGER.debug("Route ID " + routeId + " is started = " + routeStatus.isStarted());
                } else {
                    LOGGER.debug("routeStatus is NULL for routeId = " + routeId);
                }
            }
        }
        LOGGER.debug("***************  END: " + msg + "  *****************\n\n");
    }
}
