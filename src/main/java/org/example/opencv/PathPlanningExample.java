package org.example.opencv;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.*;
public class PathPlanningExample {


    public static void main(String[] args) {
        URL url = ClassLoader.getSystemResource("lib/opencv/opencv_java4110.dll");
        System.load(url.getPath());
        // 加载图像
        Mat image = Imgcodecs.imread("src/main/resources/yrcl.bmp", Imgcodecs.IMREAD_COLOR);

        if (image.empty()) {
            System.out.println("Could not open or find the image");
            return;
        }
        Point start_point = new Point(30, 465);  // 左上角作为起点
        Point end_point = new Point(150, 60); // 右下角作为终点
        // 使用A*寻路算法
        int[][] path = aStarPathPlanning(start_point, end_point, image);
        // 在图像上绘制路径
        for (int[] point : path) {
            Imgproc.circle(image, new Point(point[0], point[1]), 1, new Scalar(0,255,0), -1);
        }
        // 在图像上绘制起点和终点
        Imgproc.circle(image, start_point, 5, new Scalar(0,0,255), -1);
        Imgproc.circle(image, end_point, 5, new Scalar(255,0,0), -1);
        // 保存最终结果图像
        Imgcodecs.imwrite("path_planning_result.png", image);
        System.out.println("Processing completed. Check the output images.");
    }

    /**
     *  使用A*寻路算法
     * @param start 开始点
     * @param end 结束点
     * @param image 需要寻路的图像
     * @return 路径规划点数组
     */
    private static int[][] aStarPathPlanning(Point start, Point end, Mat image) {
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
        int[][] directions = {{-1,0}, {1,0}, {0,-1}, {0,1}, {-1,-1}, {-1,1}, {1,-1}, {1,1}};

        // 使用二维数组管理节点（替代字符串拼接的Hash）
        Node[][] nodeMap = new Node[image.rows()][image.cols()];

        // 验证起点终点可通行性
/*        if (mapData[(int)start.y][(int)start.x] < 200 ||
                mapData[(int)end.y][(int)end.x] < 200) {
            System.out.println("起点或终点不可通行");
            return new int[0][2];
        }*/

        Node startNode = new Node((int)start.x, (int)start.y);
        Node endNode = new Node((int)end.x, (int)end.y);
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
                if (mapData[ny][nx] < 200) continue;

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

}
