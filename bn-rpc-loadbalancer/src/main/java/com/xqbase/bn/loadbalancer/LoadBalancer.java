package com.xqbase.bn.loadbalancer;

import java.util.List;

/**
 * Interface that defines the operations for a software loadbalancer. A typical
 * loadbalancer minimally need a set of servers to loadbalance for, a method to
 * mark a particular server to be out of rotation and a call that will choose a
 * server from the existing list of server.
 *
 * @author Tony He
 *
 */
public interface LoadBalancer {

    /**
     * Initial list of servers.
     * This API also serves to add additional ones at a later time
     * The same logical server (host:port) could essentially be added multiple times
     * (helpful in cases where you want to give more "weightage" perhaps ..)
     *
     * @param newServers new servers to add
     */
    void addServers(List<Server> newServers);

    /**
     * Choose a server from load balancer.
     *
     * @param key An object that the load balancer may use to determine which server to return. null if
     *         the load balancer does not use this parameter.
     * @return server chosen
     */
    Server choose(Object key);

    /**
     * To be called by the clients of the load balancer to notify that a Server is down
     * else, the LB will think its still Alive until the next Ping cycle - potentially
     * (assuming that the LB Impl does a ping)
     *
     * @param server Server to mark as down
     */
    void markServerDown(Server server);

    /**
     * Get the current list of servers.
     *
     * @param availableOnly if true, only live and available servers should be returned
     */
    List<Server> getServersList(boolean availableOnly);
}
