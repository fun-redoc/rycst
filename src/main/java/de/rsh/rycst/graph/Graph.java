package de.rsh.rycst.graph;
import java.util.stream.*;

import de.rsh.rycst.utils.Pair;
import de.rsh.rycst.utils.Vec2d;

import java.util.*;

public class Graph {
   private List<Vec2d> nodes = new ArrayList<Vec2d>(); 
   private List<Pair<Vec2d, Vec2d>> edges = new ArrayList<>();
   
   /**
    * @apiNote mutating
    * @return this Graph cleared
    */
   public Graph clear() {
    nodes.clear();
    edges.clear();
    return this;
   }

   public Stream<Pair<Vec2d,Vec2d>> edges() {
     return edges.stream();
   }

   public Stream<Vec2d> nodes() {
     return nodes.stream();
   }

   /**
    * 
    * @param p Point
    * @param r Radius
    * @return maybe return a node in the r vicinity of p 
    */
  public Optional<Vec2d> nodeNear(Vec2d p, double r) {
    return nodes()
            .dropWhile((n) -> !n.isNear(p, r))
            .findFirst();
  }

  /**
   * @returns new Point on success, point of graph if already present
   */
  public Optional<Vec2d> tryAddPoint(Vec2d p) {
    var i = nodes.indexOf(p);
    if(i < 0) {
        nodes.addLast(p);
        return Optional.of(p);
    } else {
        return Optional.of(nodes.get(i));
    }
  }

  /**
   * @returns Point on success, undefined if point already in graph
   */
  public Optional<Vec2d> tryAddPointXY(double x, double y) {
    return this.tryAddPoint(new Vec2d(x, y));
  }

  /**
   * @returns Segment on success, undefined if segment already in graph (directional), or start and end point identical
   */
  public Optional<Pair<Vec2d,Vec2d>> tryAddSegment(Vec2d p1, Vec2d p2) {
    if (p1.equals(p2)) return Optional.empty();

    var sg1 = nodes().dropWhile((n) -> !p1.equals(n)).findFirst();
    var sg2 = nodes().dropWhile((n) -> !p2.equals(n)).findFirst();
    if (sg1.isEmpty() || sg2.isEmpty()) return Optional.empty();

    var newEdge = new Pair<>(sg1.get(), sg2.get());
    if(edges().anyMatch((e) -> e.equals(newEdge))) {
        return Optional.empty();
    } else {
        edges.addLast(newEdge);
        return Optional.of(newEdge);
    }
  }

  /**
   * @returns true if n not in graph after operation, false otherwise
   */
  public boolean tryRemoveNode(Vec2d n) {
    // 0. check if node is in graph
    if(nodes.indexOf(n) < 0) return true;
    // 1. remove all  edges containing this node
    edges = edges()
            .collect(() -> new ArrayList<Pair<Vec2d,Vec2d>>(), 
                     (acc,e) -> { 
                        if(!e.fst().equals(n) && !e.snd().equals(n)) {
                            acc.add(e);
                        }
                     },
                     (acc1, acc2) -> {
                        acc1.addAll(acc2);
                     });

    // 2. remuve node
    nodes.remove(n);

    return true;
  }
  @Override
  public String toString() {
    String strNodes = nodes().map(n -> n.toString()).collect(Collectors.joining(","));
    var strEdges = edges().map(e -> e.toString()).collect(Collectors.joining(","));
    return String.format("Nodes: [%s]; Edges: [%s]", strNodes, strEdges);
  }
}
