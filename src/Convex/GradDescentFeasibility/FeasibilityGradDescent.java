package Convex.GradDescentFeasibility;

import Convex.RnHalfSpace;
import Convex.LinearRn.RnPlane;
import Convex.RnPolyhedron;
import Matricies.Point;
import Matricies.PointD;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * An child class of polytope with a faster feasibility algorithm.
 *
 * @author Dov Neimand
 */
public class FeasibilityGradDescent extends RnPolyhedron {

    /**
     * The constructor.
     */
    public FeasibilityGradDescent() {
    }

    /**
     * If the feasibility function crashes, then it should save the polytope to
     * an error file. That error file can be loaded here.
     *
     * @throws IOException
     */
    public static void loadFromErrorFile() throws IOException {
        Path errorFile = Path.of("error.txt");
        PointD start = new PointD(Files.lines(errorFile).findFirst().get());

        RnPolyhedron poly = new RnPolyhedron(
                Files.lines(errorFile)
                        .filter(line -> line.startsWith("point"))
                        .map(line -> {
                            String[] pointStrings = line.replace("point ", "").split(" with normal ");
                            return new RnHalfSpace(new PointD(pointStrings[0]), new PointD(pointStrings[1]));
                        })
        );
        System.out.println(poly);

        System.out.println(new FeasibilityGradDescent(poly).fesibility(start));
    }

    /**
     * The constructor
     *
     * @param p another polytope to be copied.
     */
    public FeasibilityGradDescent(RnPolyhedron p) {
        super(p);

    }

    /**
     * The sum of the distances of the point from all the half spaces. This
     * function is not actually called as part of the feasibility algorithm.
     * This is the sum of the distances of all the half spaces to the given
     * point
     *
     * @param y the point distant from all the half spaces.
     * @return
     */
    public double sumDist(Point y) {
        return stream().parallel().mapToDouble(hs -> hs.d(y)).sum();
    }

    /**
     * The gradient of the sumDist is the sum of all the normals with half
     * spaces that exclude y. The point must be outside the polytope or an
     * element not found exception will be thrown. This function is also never
     * called. Instead the algorithm tracks the gradient as the iteration point
     * moves into more half spaces.
     *
     * @param y
     * @return
     */
    public Point gradSumDist(PointD y) {

        return stream().parallel().filter(hs -> !hs.hasElement(y)).map(hs -> hs.normal().dir())
                .reduce((a, b) -> a.plus(b)).get();
    }

    /**
     * Finds the next half space intersection from y in the given direction
     * TODO: Can the adjustments to the partition be incorporated into this
     * function?
     *
     * @param y the start point
     * @param grad the direction to look for an intersection in.
     * @return the nearest point where the ray from y hits a plane
     */
    private RnHalfSpace targetPlane(Point y, Point grad, Partition part) {

        RnHalfSpace downhillFacing = part.nearestDownhillFaceContaining(y, grad, epsilon);

        Comparator<RnHalfSpace> hsDistComp = Comparator.comparing(hs -> hs.boundary().lineIntersection(grad, y).d(y));

        if (downhillFacing == null) {
            return part.downhillExcluding(grad, epsilon).max(hsDistComp).get();
        } else {
//            return downhillFacing;//TODO: change back to old way here?
            return part.excludingSpacesBetweenHereAndThere(grad, downhillFacing.boundary()
                    .lineIntersection(grad, y), epsilon).max(hsDistComp).orElse(downhillFacing);
        }

    }

    /**
     * Updates to partition to y's new location.
     *
     * @param rollToPoint the point y is moving to
     * @param part the partition - keeping track of the half spaces y is inside
     * of and those it is outside of.
     */
    private void updatePartition(Point rollToPoint, Partition part, RnHalfSpace rollToHS) {
        part.passThroughSpaces(
                part.excluding()
                        .filter(hs -> hs.hasElement(rollToPoint, epsilon))
                        .collect(Collectors.toList())
        );

        part.enterSpace(rollToHS);

    }

    /**
     * Produces a point in this polytope that is nearish to y.
     *
     * @param y
     * @return
     */
    public Point fesibility(Point y) {

        Partition part = new Partition(y, this);
        if (part.pointIsFeasible())
            return y;

        ProjPolyManager cone = new ProjPolyManager(part);

        for (int i = 0; i <= size(); i++) {

            try {
                RnHalfSpace rollToPlane = targetPlane(y, cone.grad(), part);

                y = rollToPlane.boundary().lineIntersection(cone.grad(), y);

                updatePartition(y, part, rollToPlane);
                
                if (!part.pointIsFeasible())
                    cone.travelToNewLocalPoly(rollToPlane, y);

                else return y;

            } catch (EmptyPolytopeException epe) {
                return new PointD(1).setAll(j -> Double.NaN);
            }

        }

        throw new FailedDescentException("Polytope fesibility is taking too long.", PointD.oneD(Double.NaN), y, part);

    }

    /**
     *
     * Finds a point in the polytope. The iterative process starts with 0.
     *
     * @return
     */
    public Point fesibility() {
        return fesibility(new PointD(dim()));

    }

    /**
     * This exception is thrown if the descent failed for whatever reason. It
     * creates an error log recording the polytope that failed.
     */
    public class FailedDescentException extends RuntimeException {

        /**
         * The constructor.
         *
         * @param message
         * @param start the starting point of the iterative process.
         * @param y the location of the iteration at the time of failure.
         * @param part the partition.s
         */
        public FailedDescentException(String message, Point start, Point y, Partition part) {
            super(message + "\nThe starting point was: "
                    + start
                    + "\nThe distance to the polytope is " + sumDist(y)
                    + "\n the gradient is " + part.getGradient()
                    //                    + "\nThe polytope is :\n"
                    //                    + FeasibilityGradDescent.this.toString()
                    + "\nwriting report to error.txt");

            try {
                RnPlane.printEasyRead = false;
                BufferedWriter bw = new BufferedWriter(new FileWriter(new File("error.txt")));
                bw.write(start.toString());
                bw.newLine();
                bw.write(FeasibilityGradDescent.this.toString());
                bw.close();

            } catch (IOException ex) {
                System.err.println("Faile to save report.");
                Logger.getLogger(FeasibilityGradDescent.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        public FailedDescentException() {
            super("Descent has failed.");
        }

    }
}
