package Convex.Linear;

import Convex.ConvexSet;
import Convex.Linear.LinearSpace;
import Convex.Polytope;
import listTools.Pair1T;
import Matricies.Matrix;
import Matricies.ReducedRowEchelonDense;
import Matricies.PointDense;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Dov Neimand
 */
public class AffineSpace implements ConvexSet {

    protected LinearSpace linearSpace;
    public PointDense b;

    public PointDense getB() {
        return b;
    }

    
    /**
     * A small number
     */
    private double epsilon = 1e-8;

    /**
     * sets a zero threshold number
     *
     * @param epsilon
     */
    public void setEpsilon(double epsilon) {
        this.epsilon = epsilon;
    }

    /**
     * This affine space is the solution set to matrix*x == b. After b is added,
     * it's changed to a row instead of a column.
     *
     * @param normals
     * @param b
     */
    public AffineSpace(PointDense[] normals, PointDense b) {
        linearSpace = new LinearSpace(normals);
        this.b = b;
    }
    
    public Matrix nullMatrix(){
        return linearSpace.matrix();
    }

    /**
     * a constructor
     *
     * @param ls a linear space parallel to this affine space
     * @param onSpace a point in the affine space
     */
    public AffineSpace(LinearSpace ls, PointDense onSpace) {
        linearSpace = ls;
        if(!ls.isAllSpace())this.b = nullMatrix().mult(onSpace);
        
        p = onSpace;
    }

    @Override
    public boolean hasElement(PointDense x) {
        return nullMatrix().mult(x).equals(b);
    }

    @Override
    public boolean hasElement(PointDense x, double epsilon) {

        return nullMatrix().mult(x).equals(b, epsilon);
    }

    protected PointDense p = null;

    public boolean hasAPoint(){
        return p!= null;
    }
    
    /**
     * This method is unprotected.  It is on the caller to make sure that the
     * given point is in the affine space.
     * @param p 
     */
    public void setP(PointDense p) {
        this.p = p;
    }
    

    /**
     * A point in the affine space
     *
     * @return
     */
    public PointDense p() {

        if (p != null) return p;
                
        if (nullMatrix().isSquare() && nullMatrix().hasFullRank())
            return p = nullMatrix().solve(b);
        
        ReducedRowEchelonDense rre = new ReducedRowEchelonDense(nullMatrix());
        
        Matrix append = Matrix.fromRows(rre.getFreeVariables().map(i -> new PointDense(rre.cols).set(i, 1)));

        PointDense b2 = new PointDense(nullMatrix().rows + append.rows).setFromSubArray(b, 0);

        try {

            return p = nullMatrix().rowConcat(append).solve(b2);

        } catch (Exception ex) {

            throw new NoSuchElementException("This affine space is empty." + this);
        }
    }

    /**
     * It might be faster to compute the moore penrose psudo inverse
     *
     * @param x
     * @return
     */
    @Override
    public PointDense proj(PointDense x) {
        if (isAllSpace()) return x;
        return p().plus(linearSpace().proj(x.minus(p())));  //An older method
    }

    /**
     * The intersection of this affine space and the given affine space
     *
     * @param as another affine space
     * @return the intersection of the two spaces.
     */
    public AffineSpace intersection(AffineSpace as) {
        if(isAllSpace()) return as;
        if(as.isAllSpace()) return this;
        
        return new AffineSpace(linearSpace.intersection(as.linearSpace), b.concat(as.b));

    }

    public static AffineSpace intersection(AffineSpace[] space) {
        if (space.length == 0)
            throw new RuntimeException("Empty intersection?");
        if (space.length == 1) return space[0];

        Pair1T<AffineSpace> spacePair = new Pair1T<>(
                Arrays.copyOfRange(space, 0, space.length / 2),
                Arrays.copyOfRange(space, space.length / 2, space.length),
                spaces -> intersection(spaces));

        return spacePair.l.intersection(spacePair.r);
    }

    public static AffineSpace intersection(Stream<? extends AffineSpace> space) {
        return intersection(space.toArray(AffineSpace[]::new));
    }


    public LinearSpace linearSpace() {
        return linearSpace;
    }

    @Override
    public String toString() {
        return linearSpace().toString() + (p != null ? "\nwith point " + p : "\nb = " + b )+ "\ndim = " + subSpaceDim();
    }

    private long subSpaceDim = -2;

    /**
     * The dimension of the linear space.
     *
     * @return
     */
    public long subSpaceDim() {
        if (subSpaceDim != -2) return subSpaceDim;
        if(nullMatrix().rows == 0) return subSpaceDim = dim();
        return subSpaceDim = linearSpace().subSpaceDim();
    }

    /**
     * Gets the affine orthoganal complement of the affine space at a given
     * point.
     *
     * @param x a point on the orthognalal complement space.
     * @return an affine space orthoganal to this one that goes through the
     * given point.
     */
    public AffineSpace orthogonalComplement(PointDense x) {
        return new AffineSpace(linearSpace().OrhtogonalComplement(), x);
    }

    /**
     * An affine space that is all of the space.
     *
     * @param dim
     * @return
     */
    public static AffineSpace allSpace(int dim) {
        return new AffineSpace(LinearSpace.allSpace(0), PointDense.Origin(dim));
    }

    /**
     * This affine space as an intersection of half spaces.
     *
     * @return
     */
    public Polytope asPolytope() {

        return new Polytope(
                nullMatrix().rowStream()
                        .flatMap(row -> new Plane(p(), row)
                        .asPolytope().stream()));
    }

    /**
     * the dimension of the space containing this affine space
     *
     * @return
     */
    public int dim() {
        if(nullMatrix().rows == 0 && p != null) return p.dim();
        return nullMatrix().cols;
    }

    public boolean hasFullDimensionality() {
        return subSpaceDim() == dim();
    }

    public boolean hasIntersection(AffineSpace as) {
        if (as.hasFullDimensionality() || hasFullDimensionality()) return true;
        try {
            return intersection(as).p().isReal();
        } catch (NoSuchElementException ex) {
            return false;
        } catch (ArithmeticException ex) {
            return false;
        }
    }

    public static boolean hasIntersection(Stream<? extends AffineSpace> asStream) {
        List<AffineSpace> asList = asStream.collect(Collectors.toList());
        try {
            return AffineSpace.intersection(asList.stream()).p().isReal();
        } catch (ArithmeticException ae) {
            return false;
        }
    }

    /**
     * Creates the smallest affine space smallestContainingSubSpace the given
     * points.
     *
     * @param rowMatrix each row is one of the points in the affine space
     * @return a new affine space smallestContainingSubSpace the given points.
     */
    public static AffineSpace smallestContainingSubSpace(Matrix rowMatrix, double epsilon) {
        PointDense displacement = rowMatrix.row(0);

        Matrix displaced = new Matrix(rowMatrix.rows - 1, rowMatrix.cols).setRows(i -> rowMatrix.row(i + 1).minus(displacement));

        return new AffineSpace(LinearSpace.colSpace(displaced.independentRows(epsilon).T()), displacement);
    }

    /**
     * A point not in this space.
     *
     * @return
     */
    public PointDense notInSpace() {
        PointDense out = orthogonalComplement(p())
                .linearSpace()
                .colSpaceMatrix()
                .colStream()
                .reduce((a, b) -> a.plus(b)).get();
        return p().plus(out);
    }

    /**
     * is this space a subset of the given space
     *
     * @param containing the space that contains this space
     * @return true if the space contains this space
     */
    public boolean subsetOf(AffineSpace containing) {
        return containing.hasElement(p())
                && linearSpace().colSpaceMatrix().colStream()
                        .allMatch(col -> containing.hasElement(col.plus(p())));
    }

    /**
     * Is the given space a subset of this space
     *
     * @param subset the possible subset
     * @return true if it's a subset, false otherwise
     */
    public boolean containsAsSubset(AffineSpace subset) {
        return subset.subsetOf(this);
    }

    /**
     * The line going through these two points
     *
     * @param a
     * @param b
     * @return a line going through the two given points
     */
    public static AffineSpace twoPointsLine(PointDense a, PointDense b) {
        return PointSlopeLine(a, b.minus(a));
    }

    /**
     * A line going through the given point with the given slope
     *
     * @param a the given point
     * @param grad the given slope
     * @return a line
     */
    public static AffineSpace PointSlopeLine(PointDense a, PointDense grad) {

        return new AffineSpace(LinearSpace.colSpace(grad), a);
    }
    
    
    /**
     * Are all points in space in this affine space?
     * @return 
     */
    public boolean isAllSpace(){
        return linearSpace.isAllSpace();
    }
}
