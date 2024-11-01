package de.rsh.graph;

/**
 * vector accessor interface, ment to be used in arena allocation scenarios
 */
public interface V2<V2AccessHandle> {
    /**
     * construct an new vector 2d 
     * @param x
     * @param y
     * @return Access Handle
     */
    public V2AccessHandle c(double x, double y); 
    
    /**
     * access x coord
     * @param v access handle to the vector within the arena
     * @return the x coord
     */
    public double x(V2AccessHandle v); 

    /**
     * access y coord
     * @param v access handle to the vector within the arena
     * @return the y coord
     */
    public double y(V2AccessHandle v); 
}
