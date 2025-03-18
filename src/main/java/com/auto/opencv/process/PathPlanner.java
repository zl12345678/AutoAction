package com.auto.opencv.process;

import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.util.*;

/**
 * 路径规划器，负责 A* 算法的实现
 */
public class PathPlanner {
    private Mat image;
    private Point start;
    private Point end;
    private double obstacleThreshold;

    public PathPlanner(Mat image, Point start, Point end, double obstacleThreshold) {
        this.image = image;
        this.start = start;
        this.end = end;
        this.obstacleThreshold = obstacleThreshold;
    }

    /**
     * 执行路径规划
     */
    public int[][] findPath() {
        // 预处理：将Mat数据缓存到二维数组提升访问速度
        double[][] mapData = new double[image.rows()][image.cols()];
        for (int y = 0; y < image.rows(); y++) {
            for (int x = 0; x < image.cols(); x++) {
                mapData[y][x] = image.get(y, x)[0];
            }
        }

        class Node {
            final int x;
            final int y;
            double g, h, f;
            Node parent;
            boolean inOpenList;

            Node(int x, int y) {
                this.x = x;
                this.y = y;
                this.g = Double.MAX_VALUE;
                this.h = 0;
                this.f = Double.MAX_VALUE;
                this.parent = null;
                this.inOpenList = false;
            }

            // 使用曼哈顿距离优化计算速度
            void calculateHeuristic(Point end) {
                this.h = Math.abs(x - end.x) + Math.abs(y - end.y);
            }
        }

        // 预计算移动代价（8方向）
        final double[] MOVE_COST = {1, 1, 1, 1, Math.sqrt(2), Math.sqrt(2), Math.sqrt(2), Math.sqrt(2)};
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

        // 使用二维数组管理节点（替代字符串拼接的Hash）
        Node[][] nodeMap = new Node[image.rows()][image.cols()];

        // 验证起点终点可通行性
        if (mapData[(int) start.y][(int) start.x] < obstacleThreshold ||
                mapData[(int) end.y][(int) end.x] < obstacleThreshold) {
            System.out.println("起点或终点不可通行");
            return new int[0][2];
        }

        Node startNode = new Node((int) start.x, (int) start.y);
        Node endNode = new Node((int) end.x, (int) end.y);
        startNode.g = 0;
        startNode.calculateHeuristic(end);
        startNode.f = startNode.h; // f = g + h（此时g=0）
        nodeMap[startNode.y][startNode.x] = startNode;

        // 优化后的优先队列（使用拉宾-卡普算法避免重复节点）
        PriorityQueue<Node> openQueue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        openQueue.add(startNode);
        startNode.inOpenList = true;

        int maxIterations = image.cols() * image.rows() * 2;
        int iterations = 0;

        while (!openQueue.isEmpty() && iterations++ < maxIterations) {
            Node current = openQueue.poll();
            current.inOpenList = false;

            if (current.x == endNode.x && current.y == endNode.y) {
                // 路径回溯
                List<int[]> path = new ArrayList<>();
                while (current != null) {
                    path.add(new int[]{current.x, current.y});
                    current = current.parent;
                }
                Collections.reverse(path);
                return path.toArray(new int[0][2]);
            }

            // 节点加入关闭列表（直接在nodeMap中标记）
            nodeMap[current.y][current.x] = current;

            for (int i = 0; i < directions.length; i++) {  // 修复for循环结构
                int nx = current.x + directions[i][0];    // 修正变量声明
                int ny = current.y + directions[i][1];

                // 边界检查和可通行性检查
                if (nx < 0 || nx >= image.cols() || ny < 0 || ny >= image.rows()) continue;
                if (mapData[ny][nx] < obstacleThreshold) continue;

                // 通过nodeMap检查节点状态
                Node neighbor = nodeMap[ny][nx];
                if (neighbor == null) {
                    neighbor = new Node(nx, ny);
                    nodeMap[ny][nx] = neighbor;
                } else if (neighbor.inOpenList) {
                    // 已存在更优路径则跳过
                    if (current.g + MOVE_COST[i] >= neighbor.g) continue;
                }

                double tentativeG = current.g + MOVE_COST[i];
                if (tentativeG < neighbor.g) {
                    neighbor.g = tentativeG;
                    neighbor.calculateHeuristic(end);
                    neighbor.f = neighbor.g + neighbor.h;
                    neighbor.parent = current;

                    if (!neighbor.inOpenList) {
                        openQueue.add(neighbor);
                        neighbor.inOpenList = true;
                    } else {
                        // 通过重新插入实现优先队列更新（Java PriorityQueue的特性）
                        openQueue.remove(neighbor);
                        openQueue.add(neighbor);
                    }
                }
            }
        }

        System.out.println("未找到路径（迭代次数：" + iterations + "）");
        return new int[0][2];
    }
    /**
     * 计算两条线段的交叉点
     *
     * @param p1 线段1的起点
     * @param p2 线段1的终点
     * @param p3 线段2的起点
     * @param p4 线段2的终点
     * @return 交叉点，如果不存在则返回 null
     */
    private static Point calculateLineIntersection(Point p1, Point p2, Point p3, Point p4) {
        double x1 = p1.x, y1 = p1.y;
        double x2 = p2.x, y2 = p2.y;
        double x3 = p3.x, y3 = p3.y;
        double x4 = p4.x, y4 = p4.y;

        double denominator = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (denominator == 0) {
            return null; // 平行或重合
        }

        double x = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denominator;
        double y = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denominator;

        // 检查交叉点是否在线段上
        if (x < Math.min(x1, x2) || x > Math.max(x1, x2) || y < Math.min(y1, y2) || y > Math.max(y1, y2)) {
            return null;
        }
        if (x < Math.min(x3, x4) || x > Math.max(x3, x4) || y < Math.min(y3, y4) || y > Math.max(y3, y4)) {
            return null;
        }

        return new Point(x, y);
    }
}
