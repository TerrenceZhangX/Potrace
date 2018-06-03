package potrace;


import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.MatOfPoint;


import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;


public class Potrace {


    /**
     * This function converts source image into a boolean array
     *
     * @param srcImage
     * @return the boolean array where true stands for background and false
     * stands for foreground
     * @apiNote the output array has larger size than source image cause
     * background border is added around the image by 1 pixel
     */
    public static boolean[][] bitmapToBinary(Mat srcImage) {

        assert (srcImage != null && !srcImage.empty());

        Mat binaryImage = new Mat();
        if (srcImage.channels() == 3) {
            Mat grayImage = new Mat();
            Imgproc.cvtColor(srcImage, grayImage, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(grayImage, binaryImage,
                    207, 255, Imgproc.THRESH_BINARY);
        } else {
            Imgproc.threshold(srcImage, binaryImage,
                    207, 255, Imgproc.THRESH_BINARY);
        }

        boolean[][] result = new boolean[srcImage.rows() + 2][srcImage.cols() + 2];
        for (int row = 0; row < binaryImage.rows(); row++) {
            for (int col = 0; col < binaryImage.cols(); col++) {
                if ((int) binaryImage.get(row, col)[0] == 255) {
                    result[row + 1][col + 1] = true;
                } else {
                    result[row + 1][col + 1] = false;
                }
            }
        }

        // add white borders
        for (int col = 0; col < result[0].length; col++) {
            result[0][col] = true;
            result[result.length - 1][col] = true;
        }
        for (int row = 1; row < result.length - 1; row++) {
            result[row][0] = true;
            result[row][result[0].length - 1] = true;
        }

        return result;
    }


    /**
     * This function attempts to trace the given bitmap using the
     * given tracing parameters.
     *
     * @param bm                the given bitmap in binary array form
     * @param ListOfCurveArrays the curve result of Potrace
     */
    public static void potrace_trace(boolean[][] bm, ArrayList ListOfCurveArrays) {

        //Step 1
        ArrayList plistp = new ArrayList();
        bm_to_pathlist(bm, plistp);

        //Step 2
        process_path(plistp);

        //Step 3
        PathList_to_ListOfCurveArrays(plistp, ListOfCurveArrays);
    }


    /**
     * Decompose the given bitmap into paths. Returns a list of Path
     * objects with the fields len, pt, area filled
     *
     * @param bm     A binary bitmap which holds the imageinformations.
     * @param plistp List of Path objects
     */
    private static void bm_to_pathlist(boolean[][] bm, ArrayList<ArrayList<Path>> plistp) {

        Integer x = 0, y = 0;
        Point point;

        while (true) {
            point = findNext(bm, x, y);
            if (point == null) {
                break;
            } else {
                getContour(bm, point.x, point.y, plistp);
            }
        }

    }


    private static void getContour(boolean[][] bm, Integer x, Integer y,
                                   ArrayList<ArrayList<Path>> plistp) {

        //Step 1: Find Path
        Path Contur = findpath(bm, new IntPoint(x, y));

        //Step 2:
        Xor_Path(bm, Contur);

        outputMatrix(bm);

        ArrayList<Path> PolyPath = new ArrayList<Path>();

        // only area > turdsize is taken
        if (Contur.area > Constant.turdsize) {
            PolyPath.add(Contur); // Path with index 0 is a conture
            plistp.add(PolyPath);
        }


        Point point;
        while (true) {
            point = findNext(bm, x, y, Contur);
            if (point == null) {
                break;
            }
            x = point.x;
            y = point.y;


            Path Hole = findpath(bm, new IntPoint(x, y));
            Xor_Path(bm, Hole);

            if (Hole.area > Constant.turdsize) {
                PolyPath.add(Hole); // Path with index > 0 is a hole,
            }

            while (true) {    // 13.07.12 von if auf while
                point = findNext(bm, x, y, Hole);
                if (point == null) {
                    break;
                }
                x = point.x;
                y = point.y;
                getContour(bm, x, y, plistp);
            }
        }
    }


    /**
     * Searches a x and a y such that source[x,y] = true and source[x+1,y] false.
     * If this not exists, false will be returned else the result is true.
     *
     * @param bm a binary matrix
     * @param x  x index in the source Matrix
     * @param y  y index in the source Matrix
     * @return
     */
    private static Point findNext(boolean[][] bm, Integer x, Integer y) {

        for (int col = 1; col < bm[0].length - 1; col++) {
            for (int row = 0; row < bm.length - 1; row++) {
                // black found
                if (!bm[row + 1][col]) {
                    x = row;
                    y = col;
                    return new Point(row, col);
                }
            }
        }

        return null;

    }


    /**
     * Searches a x and a y inside the Path P such that source[x,y] = true and source[x+1,y] false.
     * If this not exists, false will be returned else the result is true.
     *
     * @param Matrix a Binary Matrix
     * @param x      x index in the source Matrix
     * @param y      y index in the source Matrix
     * @param P
     * @return
     */
    private static Point findNext(boolean[][] Matrix, int x, int y, Path P) {

        int i = 0;
        int n = P.pt.length;
        ArrayList<MonotonInterval> MonotonIntervals = P.MonotonIntervals;
        if (MonotonIntervals.size() == 0) {
            return null;
        }

        MonotonInterval MI = MonotonIntervals.get(0);
        MI.ResetCurrentID(n);
        y = P.pt[MI.CurrentID].Y;
        ArrayList<MonotonInterval> CurrentIntervals = new ArrayList<MonotonInterval>();
        CurrentIntervals.add(MI);
        MI.CurrentID = MI.Min();

        while (i + 1 < MonotonIntervals.size()
                && (MonotonIntervals.get(i + 1)).MinY(P.pt) == y) {

            MI = MonotonIntervals.get(i + 1);
            MI.ResetCurrentID(n);
            CurrentIntervals.add(MI);
            i++;
        }

        while (CurrentIntervals.size() > 0) {

            for (int k = 0; k < CurrentIntervals.size() - 1; k++) {
                int x1 = P.pt[(CurrentIntervals.get(k)).CurrentID].X + 1;
                int x2 = P.pt[(CurrentIntervals.get(k + 1)).CurrentID].X;

                for (x = x1; x <= x2; x++)
                    if (!Matrix[x][y]) {
                        x--;
                        return new Point(x, y);
                    }
                k++;
            }

            y++;
            for (int j = CurrentIntervals.size() - 1; j >= 0; j--) {

                MonotonInterval M = CurrentIntervals.get(j);

                if (y > M.MaxY(P.pt)) {
                    CurrentIntervals.remove(j);
                    continue;
                }
                int CID = M.CurrentID;
                do {
                    if (M.Increasing) {
                        CID = Math.mod(CID + 1, n);
                    } else {
                        CID = Math.mod(CID - 1, n);
                    }
                } while (P.pt[CID].Y < y);

                M.CurrentID = CID;
            }

            // Add Items of MonotonIntervals with Miny==y
            while (i + 1 < MonotonIntervals.size() && (MonotonIntervals.get(i + 1)).MinY(P.pt) == y) {

                MonotonInterval NewInt = MonotonIntervals.get(i + 1);
                int j = 0;
                // search the correct x-Position
                int _x = P.pt[NewInt.Min()].X;

                while (j < CurrentIntervals.size()
                        && (_x > P.pt[(CurrentIntervals.get(j)).CurrentID].X)) {
                    j++;
                }
                CurrentIntervals.add(j, NewInt);
                NewInt.ResetCurrentID(n);
                i++;
            }
        }

        return null;
    }


    private static void process_path(ArrayList plistp) {

    }

    private static void PathList_to_ListOfCurveArrays(ArrayList plistp, ArrayList ListOfCurveArrays) {

    }

    /**
     * Compute a path in the binary matrix.
     * Start path at the point (x0,x1), which must be an upper left corner
     * of the path. Also compute the area enclosed by the path. Return a
     * new path_t object, or NULL on error (note that a legitimate path
     * cannot have length 0).
     * We omit turnpolicies and sign
     *
     * @param Matrix Binary Matrix
     * @param Start  start searching point
     * @return
     */
    private static Path findpath(boolean[][] Matrix, IntPoint Start) {

        ArrayList<IntPoint> L = new ArrayList<IntPoint>();

        Direction Dir = Direction.North;
        int x;
        int y;
        int area = 0;
        int diry = -1;
        x = Start.X;
        y = Start.Y;

        do {
            L.add(new IntPoint(x, y));
            int _y = y;
            int[] result = findNextTrace(Matrix, x, y, Dir);
            x = result[0];
            y = result[1];
            Dir = Direction.valueOf(Direction.getName(result[2]));
            diry = _y - y;
            area += x * diry;
        } while ((x != Start.X) || (y != Start.Y));

        if (L.size() == 0) {
            return null;
        }

        Path result = new Path();
        result.pt = new IntPoint[L.size()];
        result.area = area;

        for (int i = 0; i < L.size(); i++) {
            result.pt[i] = L.get(i);
        }

        // Shift 1 to be compatible with Potrace
        if (result.pt.length > 0) {
            IntPoint P = result.pt[result.pt.length - 1];
            for (int i = result.pt.length - 1; i >= 0; i--) {
                if (i > 0) {
                    result.pt[i] = result.pt[i - 1];
                } else {
                    result.pt[0] = P;
                }
            }
        }

        result.MonotonIntervals = GetMonotonIntervals(result.pt);

        return result;
    }


    /**
     * @param Matrix
     * @param x
     * @param y
     * @param Dir
     * @return
     */
    private static int[] findNextTrace(boolean[][] Matrix, int x, int y, Direction Dir) {

        if (Dir == Direction.West) {
            if (!Matrix[x + 1][y + 1]) {
                y++;
                Dir = Direction.North;
            } else {
                if (!Matrix[x + 1][y]) {
                    x++;
                    Dir = Direction.West;
                } else {
                    y--;
                    Dir = Direction.South;
                }
            }
        } else if (Dir == Direction.South) {
            if (!Matrix[x + 1][y]) {
                x++;
                Dir = Direction.West;
            } else {
                if (!Matrix[x][y]) {
                    y--;
                    Dir = Direction.South;
                } else {
                    x--;
                    Dir = Direction.East;
                }
            }
        } else if (Dir == Direction.East) {
            if (!Matrix[x][y]) {
                y--;
                Dir = Direction.South;
            } else {
                if (!Matrix[x][y + 1]) {
                    x--;
                    Dir = Direction.East;
                } else {
                    y++;
                    Dir = Direction.North;
                }
            }
        } else if (Dir == Direction.North) {
            if (!Matrix[x][y + 1]) {
                x--;
                Dir = Direction.East;
            } else {
                if (!Matrix[x + 1][y + 1]) {
                    y++;
                    Dir = Direction.North;
                } else {
                    x++;
                    Dir = Direction.West;
                }
            }
        }

        int[] result = new int[3];
        result[0] = x;
        result[1] = y;
        result[2] = Dir.getValue();

        return result;
    }

    private static ArrayList<MonotonInterval> GetMonotonIntervals(IntPoint[] Pts) {

        ArrayList<MonotonInterval> result = new ArrayList<MonotonInterval>();
        int n = Pts.length;
        if (n == 0) {
            return result;
        }


        //Step 1: Divide contour into several segments
        ArrayList<MonotonInterval> L = new ArrayList<MonotonInterval>();

        //----- Start with Strong Monoton (Pts[i].y < Pts[i+1].y) or (Pts[i].y > Pts[i+1].y)
        int FirstStrongMonoton = 0;
        while (Pts[FirstStrongMonoton].Y == Pts[FirstStrongMonoton + 1].Y) {
            FirstStrongMonoton++;
        }
        boolean Up = (Pts[FirstStrongMonoton].Y < Pts[FirstStrongMonoton + 1].Y);
        MonotonInterval Interval = new MonotonInterval(Up, FirstStrongMonoton, FirstStrongMonoton);
        L.add(Interval);

        int i = FirstStrongMonoton;
        do {
            // Interval.to = i;
            if (Pts[i].Y == Pts[Math.mod(i + 1, n)].Y
                    || Up == (Pts[i].Y < Pts[Math.mod(i + 1, n)].Y)) {
                Interval.to = i;
            } else {
                Up = (Pts[i].Y < Pts[Math.mod(i + 1, n)].Y);
                Interval = new MonotonInterval(Up, i, i);
                L.add(Interval);
            }
            i = Math.mod(i + 1, n);
        } while (i != FirstStrongMonoton);


        //Step 2: Make the number of segments even
        if (L.size() / 2 * 2 != L.size()) {// Connect the Last with first
            MonotonInterval M0 = L.get(0);
            MonotonInterval ML = L.get(L.size() - 1);
            M0.from = ML.from;
            L.remove(L.size() - 1);
        }


        //Step 3: Order the segments by y-value(first-key) and x-value(second-key)
        //where the segment with lowest x-value and y-value ranks first

        //----- order now by the min y - value of interval to result
        // and as second Key by the x-value
        //
        while (L.size() > 0) {
            MonotonInterval M = L.get(0);
            i = 0;
            // order by y-value
            while (i < result.size()
                    && Pts[M.Min()].Y > Pts[(result.get(i)).Min()].Y) {
                i++;
            }
            // order by x- value as second Key
            while (i < result.size()
                    && Pts[M.Min()].Y == Pts[(result.get(i)).Min()].Y
                    && (Pts[M.Min()].X > (Pts[(result.get(i)).Min()].X))) {
                i++;
            }
            result.add(i, M);
            L.remove(0);
        }
        return result;
    }

    private static void Xor_Path(boolean[][] Matrix, Path P) {

        int i = 0;
        int n = P.pt.length;

        ArrayList<MonotonInterval> MonotonIntervals = P.MonotonIntervals;
        if (MonotonIntervals.size() == 0) {
            return;
        }
        MonotonInterval MI = MonotonIntervals.get(0);
        MI.ResetCurrentID(n);

        int y = P.pt[MI.CurrentID].Y;
        ArrayList<MonotonInterval> CurrentIntervals = new ArrayList<MonotonInterval>();
        CurrentIntervals.add(MI);
        MI.CurrentID = MI.Min();

        while (i + 1 < MonotonIntervals.size()
                && MonotonIntervals.get(i + 1).MinY(P.pt) == y) {
            MI = MonotonIntervals.get(i + 1);
            MI.ResetCurrentID(n);
            CurrentIntervals.add(MI);
            i++;
        }

        while (CurrentIntervals.size() > 0) {   // invertLine

            for (int k = 0; k < CurrentIntervals.size() - 1; k++) {

                int x1 = P.pt[(CurrentIntervals.get(k)).CurrentID].X + 1;
                int x2 = P.pt[(CurrentIntervals.get(k + 1)).CurrentID].X;

                for (int x = x1; x <= x2; x++) {
                    Matrix[x][y] = !Matrix[x][y];
                }
                k++;
            }

            y++;
            for (int j = CurrentIntervals.size() - 1; j >= 0; j--) {

                MonotonInterval M = CurrentIntervals.get(j);

                if (y > M.MaxY(P.pt)) {
                    CurrentIntervals.remove(j);
                    continue;
                }
                int CID = M.CurrentID;
                do {
                    if (M.Increasing)
                        CID = Math.mod(CID + 1, n);
                    else
                        CID = Math.mod(CID - 1, n);
                } while (P.pt[CID].Y < y);

                M.CurrentID = CID;
            }

            // Add Items of MonotonIntervals with Down.y==y
            while (i + 1 < MonotonIntervals.size()
                    && (MonotonIntervals.get(i + 1)).MinY(P.pt) == y) {

                MonotonInterval NewInt = MonotonIntervals.get(i + 1);
                int j = 0;

                // search the correct x-Position
                int _x = P.pt[NewInt.Min()].X;
                while (j < CurrentIntervals.size()
                        && _x > P.pt[(CurrentIntervals.get(j)).CurrentID].X) {
                    j++;
                }
                CurrentIntervals.add(j, NewInt);
                NewInt.ResetCurrentID(n);
                i++;
            }
        }
    }

    //Below is for test
    public static void main(String[] args) {

        String dllPath = "C:\\OpenCV\\opencv\\build\\java\\x64\\opencv_java320.dll";
        System.load(dllPath);

        testBm_to_pathlist();
    }

    private static void testBitmapToBinary() {

        String filePath = "E:\\Java_Projects\\Potrace\\resources\\sourceEntireImages\\4a.png";
        Mat srcImage = Imgcodecs.imread(filePath);
        boolean[][] result = bitmapToBinary(srcImage);

        outputMatrix(result);
    }

    private static void testBm_to_pathlist() {

        String filePath = "E:\\Java_Projects\\Potrace\\resources\\sourceEntireImages\\11a.png";
        Mat srcImage = Imgcodecs.imread(filePath);


        boolean[][] matrix = bitmapToBinary(srcImage);

        outputMatrix(matrix);

        ArrayList<ArrayList<Path>> ListOfCurveArray = new ArrayList<>();
        bm_to_pathlist(matrix, ListOfCurveArray);

        System.out.println();
    }


    /**
     * Output the given binary matrix in 0/1 form
     *
     * @param matrix
     */
    private static void outputMatrix(boolean[][] matrix) {

        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {

                if (matrix[row][col]) {
                    System.out.print(0);
                } else {
                    System.out.print(1);
                }
            }
            System.out.println();
        }

        System.out.println();
        System.out.println();
    }
}
