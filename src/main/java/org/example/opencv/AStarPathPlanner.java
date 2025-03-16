// AStarPathPlanner.java
package org.example.opencv;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import java.util.*;

public class AStarPathPlanner {
    private final Mat mapData;
    private final Point start;
    private final Point end;
    private final double obstacleThreshold;

    public AStarPathPlanner(Mat mapData, Point start, Point end, double obstacleThreshold) {
        this.mapData = mapData.clone();
        this.start = start.clone();
        this.end = end.clone();
        this.obstacleThreshold = obstacleThreshold;
    }

    // 主函数
    public int[][] findPath() {
        // 预处理地图数据
        double[][] processedMap = preprocessMapData();
        
        Node startNode = new Node((int)start.x, (int)start.y);
        Node endNode = new Node((int)end.x, (int)end.y);
        
        // 验证起点终点合法性
        if (!isValidNode(startNode, processedMap) ||
            !isValidNode(endNode, processedMap)) {
            return new int[0][2];
        }

        return aStarSearch(startNode, endNode, processedMap);
    }

    private double[][] preprocessMapData() {
        double[][] data = new double[mapData.rows()][mapData.cols()];
        for (int y = 0; y < mapData.rows(); y++) {
            for (int x = 0; x < mapData.cols(); x++) {
                data[y][x] = mapData.get(y, x)[0];
            }
        }
        return data;
    }

    // A*路径规划
    private int[][] aStarSearch(Node startNode, Node endNode, double[][] mapData) {
        final int cols = mapData[0].length;
        final int rows = mapData.length;

        // 初始化节点管理二维数组
        Node[][] nodeMap = new Node[rows][cols];
        PriorityQueue<Node> openQueue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

        // 8方向移动配置
        final double[] MOVE_COST = {1,1,1,1, Math.sqrt(2),Math.sqrt(2),Math.sqrt(2),Math.sqrt(2)};
        int[][] directions = {{-1,0}, {1,0}, {0,-1}, {0,1}, {-1,-1}, {-1,1}, {1,-1}, {1,1}};

        // 初始化起点
        startNode.g = 0;
        startNode.calculateHeuristic(end);
        startNode.f = startNode.g + startNode.h;
        nodeMap[startNode.y][startNode.x] = startNode;
        openQueue.add(startNode);
        startNode.inOpenList = true;

        int maxIterations = cols * rows * 2;
        int iterations = 0;

        while (!openQueue.isEmpty() && iterations++ < maxIterations) {
            Node current = openQueue.poll();
            current.inOpenList = false;

            // 到达终点
            if (current.x == endNode.x && current.y == endNode.y) {
                return reconstructPath(current);
            }

            // 标记为已关闭
            nodeMap[current.y][current.x] = current;

            // 遍历所有移动方向
            for (int i = 0; i < directions.length; i++) {
                int nx = current.x + directions[i][0];
                int ny = current.y + directions[i][1];

                // 边界检查
                if (nx < 0 || nx >= cols || ny < 0 || ny >= rows) continue;

                // 障碍物检查（使用类成员变量threshold）
                if (mapData[ny][nx] < this.obstacleThreshold) continue;

                // 获取或创建邻居节点
                Node neighbor = nodeMap[ny][nx];
                if (neighbor == null) {
                    neighbor = new Node(nx, ny);
                    nodeMap[ny][nx] = neighbor;
                }

                // 计算新的移动代价
                double tentativeG = current.g + MOVE_COST[i];

                // 发现更优路径
                if (tentativeG < neighbor.g) {
                    neighbor.g = tentativeG;
                    neighbor.calculateHeuristic(end);
                    neighbor.f = neighbor.g + neighbor.h;
                    neighbor.parent = current;

                    if (!neighbor.inOpenList) {
                        openQueue.add(neighbor);
                        neighbor.inOpenList = true;
                    } else {
                        // 更新优先队列中的节点优先级
                        openQueue.remove(neighbor);
                        openQueue.add(neighbor);
                    }
                }
            }
        }

        System.out.println("Path not found (iterations: " + iterations + ")");
        return new int[0][2];
    }

    // 路径回溯
    private int[][] reconstructPath(Node endNode) {
        LinkedList<int[]> path = new LinkedList<>();
        Node current = endNode;

        while (current != null) {
            path.addFirst(new int[]{current.x, current.y});
            current = current.parent;
        }

        return path.toArray(new int[0][2]);
    }

    // 检查节点是否合法
    private boolean isValidNode(Node node, double[][] mapData) {
        return node.x >= 0 && node.x < mapData[0].length &&
               node.y >= 0 && node.y < mapData.length &&
               mapData[node.y][node.x] >= obstacleThreshold;
    }

    // 内部节点类
    private static class Node {
        final int x, y;
        double g, h, f;
        Node parent;
        boolean inOpenList;

        Node(int x, int y) {
            this.x = x;
            this.y = y;
            resetCosts();
        }

        // 重置节点的代价
        void resetCosts() {
            this.g = Double.MAX_VALUE;
            this.h = 0;
            this.f = Double.MAX_VALUE;
            this.parent = null;
            this.inOpenList = false;
        }

        // 计算启发式值
        void calculateHeuristic(Point target) {
            this.h = Math.abs(x - target.x) + Math.abs(y - target.y);
        }
    }
}
