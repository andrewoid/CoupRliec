package Convex.thesisProjectionIdeas.GradDescentFeasibility;

import Convex.HalfSpace;
import Convex.Polytope;
import Matricies.PointDense;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An child class of polytope with a faster feasibility algorithm.
 *
 * @author Dov Neimand
 */
public class GradDescentFeasibility extends Polytope {

    /**
     * The constructor.
     */
    public GradDescentFeasibility() {
    }

    public static void loadFromErrorFile() throws IOException {
        Path errorFile = Path.of("error.txt");
        PointDense start = new PointDense(Files.lines(errorFile).findFirst().get());

        Polytope poly = new Polytope(
                Files.lines(errorFile)
                        .filter(line -> line.startsWith("point"))
                        .map(line -> {
                            String[] pointStrings = line.replace("point ", "").split(" with normal ");
                            return new HalfSpace(new PointDense(pointStrings[0]), new PointDense(pointStrings[1]));
                        })
            );
        
        System.out.println(new GradDescentFeasibility(poly).fesibility(start));
    }

    /**
     * The constructor
     *
     * @param p another polytope to be copied.
     */
    public GradDescentFeasibility(Polytope p) {
        super(p);

    }

    /**
     * The sum of the distances of the point from all the half spaces
     *
     * @param y
     * @return
     */
    public double sumDist(PointDense y) {
        return stream().parallel().mapToDouble(hs -> hs.d(y)).sum();
    }

    /**
     * The gradient of the sumDist is the sum of all the normals. The point must
     * be outside the polytope or an element not found exception will be thrown.
     *
     * @param y
     * @return
     */
    public PointDense gradSumDist(PointDense y) {

        return gradSumDist(y, stream().parallel().filter(hs -> !hs.hasElement(y)));

    }

    public PointDense gradSumDist(PointDense y, Stream<HalfSpace> containing) {
        return containing.map(hs -> hs.normal().dir())
                .reduce((a, b) -> a.plus(b)).get();
    }

    /**
     * Finds the next half space intersection from y in the given direction
     * TODO: instead of moving to the neares plane, move to the farthest
     * halfspace that does not include y without leaving any half spaces that do
     * include y so as to be in as many half spaces as possible. It may be
     * necessary to organize the intersections into a list.
     *
     * @param y the start point
     * @param grad the direction to look for an intersection in.
     * @return the nearest point where the ray from y hits a plane
     */
    private HalfSpace targetPlane(PointDense y, PointDense grad, Partition part) {

        HalfSpace downhillFacing = part.nearestDownhillFaceContaining(y, grad, epsilon);

        if (downhillFacing == null) {
            return part.downhillExcluding(grad, epsilon)
                    .max(Comparator.comparing(
                            hs -> hs.boundary().lineIntersection(grad, y).d(y)
                    )
                    ).get();
        } else {
            return part.excludingSpacesBetweenHereAndThere(grad, downhillFacing.boundary()
                    .lineIntersection(grad, y), epsilon)
                    .max(Comparator.comparing(
                            hs -> hs.boundary().lineIntersection(grad, y).d(y)
                    )
                    ).orElse(downhillFacing);
        }

    }

    private void rollThroughSpaces(PointDense rollToPoint, Partition part) {

        part.passThroughSpaces(
                part.excluding()
                        .filter(hs -> hs.hasElement(rollToPoint, epsilon))
                        .collect(Collectors.toList())
        );
    }

    /**
     * Produces a point in this polytope that is nearish to y.
     *
     * @param y
     * @return
     */
    public PointDense fesibility(PointDense y) {

        Partition part = new Partition(y, this);
        if (part.pointIsFeasible()) return y;

        LocalPolyhedralCone cone = new LocalPolyhedralCone(part);

        PointDense start = y;      //TODO remove

        for (int i = 0; i <= size(); i++) {

            try {
                
                HalfSpace rollToPlane = targetPlane(y, cone.grad(), part);

                PointDense oldY = new PointDense(y);//TODO: remove from final code

                y = rollToPlane.boundary().lineIntersection(cone.grad(), y);

                if (oldY.d(y) < epsilon) 
                    System.out.println("The new point is too close to the old point.  oldY = "
                            + oldY);
                

                if (!cone.hasElement(y, epsilon * 100)) {
                    throw new FailedDescentException("This point is outside the previouse cone. distMoved = "
                            + oldY.d(y), start, y, part);
                }

                rollThroughSpaces(y, part);

                part.enterSpace(rollToPlane);

                if (!part.pointIsFeasible()) {
                    cone.addHalfSpace(rollToPlane, y);
                } else {
                    if (!hasElement(y, epsilon * 100)) //TODO:  once the algorithm works, this should be deleted.
                        throw new FailedDescentException("The feasibility point "
                                + "found is outside the polytope.", start, y, part);
                    
                    return y;
                }
            } catch (NoSuchElementException nsee) {//TODO: remove once everything is working
                throw new FailedDescentException(nsee.getMessage(), start, y, part);
            }

        }

        throw new FailedDescentException("Polytope fesibility is taking too long.", start, y, part);

    }

    /**
     *
     * Finds a point in the polytope. The iterative process starts with 0.
     *
     * @return
     */
    public PointDense fesibility() {
        return fesibility(new PointDense(dim()));

    }

    public class FailedDescentException extends RuntimeException {

        public FailedDescentException(String message, PointDense start, PointDense y, Partition part) {
            super(message + "\nThe starting point was: "
                    + start
                    + "\nThe distance to the polytope is " + sumDist(y)
                    + "\n the gradient is " + part.getGradient()
                    + "\nThe polytope is :\n"
                    + GradDescentFeasibility.this.toString()
                    + "\nwriting report to error.txt");

            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(new File("error.txt")));
                bw.write(y.toString());
                bw.newLine();
                bw.write(GradDescentFeasibility.this.toString());
                bw.close();

            } catch (IOException ex) {
                Logger.getLogger(GradDescentFeasibility.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }
}
