package com.auto.opencv.process;

import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.*;

/**
 * 路径规划器，负责 A* 算法的实现
 */
public class PathPlanner {
    private Mat image; // 地图图像
    private Point start; // 起点
    private Point end; // 终点
    private double obstacleThreshold; // 障碍物阈值（像素值大于此值为障碍物）
    private int[][] path;

    public PathPlanner(Mat image, Point start, Point end, double obstacleThreshold) {
        this.image = image;
        this.start = start;
        this.end = end;
        this.obstacleThreshold = obstacleThreshold; // 以下比较 > obstacleThreshold，即白色为障碍物，黑色可通行
    }
    public int[][] findPath() {
        // 原有的 A* 算法逻辑
        int[][] rawPath = findRawPath(); // 假设这是原有的 A* 算法生成的路径

        // 将路径转换为 List<Point>
        List<Point> path = new ArrayList<>();
        for (int[] point : rawPath) {
            path.add(new Point(point[0], point[1]));
        }
        List<Point> points = simplifyPath(path);
        // 拟人化路径
        List<Point> humanizedPath = humanizePath(points);
        int[][] finalPath = new int[humanizedPath.size()][2];
        for (int i = 0; i < humanizedPath.size(); i++) {
            finalPath[i][0] = (int) humanizedPath.get(i).x;
            finalPath[i][1] = (int) humanizedPath.get(i).y;
        }
/*        int[][] finalPath = new int[points.size()][2];
        for (int i = 0; i < points.size(); i++) {
            finalPath[i][0] = (int) points.get(i).x;
            finalPath[i][1] = (int) points.get(i).y;
        }*/
        // 先简化原始路径
        System.out.println(Arrays.deepToString(finalPath));
        return finalPath;
    }

    // 简化路径点
    public static List<Point> simplifyPath(List<Point> path) {
        if (path.isEmpty()) return new ArrayList<>();  // 空路径直接返回

        List<Point> simplified = new ArrayList<>();
        simplified.add(path.get(0));
        if (path.size() == 1) return simplified;  // 只有一个点时直接返回
        for (int i = 1; i < path.size() - 1; i++) {
            Point prev = simplified.get(simplified.size() - 1);
            Point current = path.get(i);
            Point next = path.get(i + 1);

            // 向量叉积判断是否共线
            double cross = (current.x - prev.x) * (next.y - prev.y)
                    - (current.y - prev.y) * (next.x - prev.x);
            if (Math.abs(cross) > 1e-6) { // 非共线则保留
                simplified.add(current);
            }
        }
        simplified.add(path.get(path.size() - 1));
        return simplified;
    }
    /**
     * 执行路径规划
     *
     * @return 返回路径的坐标数组，每个元素是一个二维数组 [x, y]
     */
    public int[][] findRawPath() {
        // 预处理：将Mat数据缓存到二维数组提升访问速度
        double[][] mapData = new double[image.rows()][image.cols()];
        for (int y = 0; y < image.rows(); y++) {
            for (int x = 0; x < image.cols(); x++) {
                mapData[y][x] = image.get(y, x)[0]; // 获取像素值
            }
        }

        // 内部类：表示路径规划中的一个节点
        class Node {
            final int x; // 节点的x坐标
            final int y; // 节点的y坐标
            double g; // 从起点到当前节点的实际代价
            double h; // 从当前节点到终点的启发式估计代价
            double f; // 总代价 f = g + h
            Node parent; // 父节点，用于路径回溯
            boolean inOpenList; // 标记节点是否在开放列表中

            Node(int x, int y) {
                this.x = x;
                this.y = y;
                this.g = Double.MAX_VALUE; // 初始化为最大值
                this.h = 0;
                this.f = Double.MAX_VALUE;
                this.parent = null;
                this.inOpenList = false;
            }

            // 使用曼哈顿距离计算启发式代价
            void calculateHeuristic(Point end) {
                this.h = Math.abs(x - end.x) + Math.abs(y - end.y);
            }

            // 计算靠近障碍物的额外代价
            double calculateObstacleCost(double[][] mapData, double obstacleThreshold) {
                double cost = 0;
                int searchRadius = 10; // 搜索半径，可以根据需要调整
                for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                    for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < mapData[0].length && ny >= 0 && ny < mapData.length) {
                            if (mapData[ny][nx] > obstacleThreshold) {
                                // 距离越近，代价越高
                                double distance = Math.sqrt(dx * dx + dy * dy);
                                cost += 10.0 / (distance + 1); // 可以根据需要调整系数
                            }
                        }
                    }
                }
                return cost;
            }
        }

        // 预计算移动代价（8方向）
        final double[] MOVE_COST = {1, 1, 1, 1, Math.sqrt(2), Math.sqrt(2), Math.sqrt(2), Math.sqrt(2)};
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

        // 使用二维数组管理节点（替代字符串拼接的Hash）
        Node[][] nodeMap = new Node[image.rows()][image.cols()];

        // 验证起点和终点的可通行性
        if (mapData[(int) start.y][(int) start.x] > obstacleThreshold ||
                mapData[(int) end.y][(int) end.x] > obstacleThreshold) {
            System.out.println("起点或终点不可通行");
            return new int[0][2]; // 返回空路径
        }

        // 初始化起点节点
        Node startNode = new Node((int) start.x, (int) start.y);
        Node endNode = new Node((int) end.x, (int) end.y);
        startNode.g = 0; // 起点的实际代价为0
        startNode.calculateHeuristic(end); // 计算启发式代价
        startNode.f = startNode.h; // f = g + h（此时g=0）
        nodeMap[startNode.y][startNode.x] = startNode;

        // 优化后的优先队列（使用拉宾-卡普算法避免重复节点）
        PriorityQueue<Node> openQueue = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        openQueue.add(startNode);
        startNode.inOpenList = true;

        int maxIterations = image.cols() * image.rows() * 2; // 最大迭代次数
        int iterations = 0;

        // A* 算法主循环
        while (!openQueue.isEmpty() && iterations++ < maxIterations) {
            Node current = openQueue.poll(); // 取出总代价最小的节点
            current.inOpenList = false;

            // 如果当前节点是终点，则回溯路径
            if (current.x == endNode.x && current.y == endNode.y) {
                List<int[]> path = new ArrayList<>();
                while (current != null) {
                    path.add(new int[]{current.x, current.y}); // 将节点坐标加入路径
                    current = current.parent; // 回溯到父节点
                }
                Collections.reverse(path); // 反转路径，使其从起点到终点
                return path.toArray(new int[0][2]); // 返回路径
            }

            // 将当前节点加入关闭列表（直接在nodeMap中标记）
            nodeMap[current.y][current.x] = current;

            // 遍历8个方向
            for (int i = 0; i < directions.length; i++) {
                int nx = current.x + directions[i][0]; // 邻居节点的x坐标
                int ny = current.y + directions[i][1]; // 邻居节点的y坐标

                // 边界检查和可通行性检查
                if (nx < 0 || nx >= image.cols() || ny < 0 || ny >= image.rows()) continue; // 超出边界则跳过
                if (mapData[ny][nx] > obstacleThreshold) continue; // 障碍物则跳过

                // 通过nodeMap检查节点状态
                Node neighbor = nodeMap[ny][nx];
                if (neighbor == null) {
                    neighbor = new Node(nx, ny); // 创建新节点
                    nodeMap[ny][nx] = neighbor;
                } else if (neighbor.inOpenList) {
                    // 已存在更优路径则跳过
                    if (current.g + MOVE_COST[i] >= neighbor.g) continue;
                }

                // 计算从起点到邻居节点的实际代价
                double tentativeG = current.g + MOVE_COST[i];
                // 增加靠近障碍物的额外代价
                double obstacleCost = neighbor.calculateObstacleCost(mapData, obstacleThreshold);
                tentativeG += obstacleCost;

                if (tentativeG < neighbor.g) {
                    neighbor.g = tentativeG; // 更新实际代价
                    neighbor.calculateHeuristic(end); // 更新启发式代价
                    neighbor.f = neighbor.g + neighbor.h; // 更新总代价
                    neighbor.parent = current; // 更新父节点

                    if (!neighbor.inOpenList) {
                        openQueue.add(neighbor); // 加入开放列表
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
        return new int[0][2]; // 返回空路径
    }
    private List<Point> humanizePath(List<Point> path) {
        List<Point> humanizedPath = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < path.size() - 1; i++) {
            Point current = path.get(i);
            Point next = path.get(i + 1);

            // 插入当前点
            humanizedPath.add(current);

            // 计算中点
            double midX = (current.x + next.x) / 2;
            double midY = (current.y + next.y) / 2;

            // 在中点附近引入随机偏移
            double offsetX = (random.nextDouble() - 0.5) * 5; // 随机偏移范围：±2.5
            double offsetY = (random.nextDouble() - 0.5) * 5;
            humanizedPath.add(new Point(midX + offsetX, midY + offsetY));
        }

        // 添加最后一个点
        humanizedPath.add(path.get(path.size() - 1));

        return humanizedPath;
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
