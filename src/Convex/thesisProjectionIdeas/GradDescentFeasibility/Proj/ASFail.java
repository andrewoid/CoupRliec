package Convex.thesisProjectionIdeas.GradDescentFeasibility.Proj;

import Convex.thesisProjectionIdeas.GradDescentFeasibility.Proj.ASKeys.ASKey;
import Convex.Linear.AffineSpace;
import Convex.Linear.Plane;
import Convex.thesisProjectionIdeas.GradDescentFeasibility.Proj.ASKeys.ASKeyRI;
import Matricies.Point;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks if the underlying affine space meets the necessary criteria.
 *
 * @author dov
 */
public class ASFail {

    /**
     * Links to the affine space that this ASFail checks for necessary criteria
     */
    public final ASNode asNode;

    /**
     * It the projection onto the personal polytope of the underlying affine
     * space is not in that space, then it is stored here.
     */
    public Point projOntoPersoanlPoly;

    /**
     * Does this affine space meet the necessary conditions.
     */
    public boolean mightContProj; //TODO: Check if I can safely remove this.

    /**
     * The constructor
     *
     * @param asNode The affine node to be checked to meet the necessary
     * conditions
     */
    public ASFail(ASNode asNode) {
        this.asNode = asNode;
        projOntoPersoanlPoly = null;
    }

    /**
     * The constructor
     *
     * @param plane A single plane that is the affine space to be checked for
     * necessary conditions.
     * @param planeIndex the index of the affine space in the list in
     * ProjPolytope
     * @param map A set of all the affine space nodes that have been saved.
     */
    public ASFail(Plane plane, int planeIndex, ConcurrentHashMap<ASKey, ASNode> map) {
        this(ASNode.factory(plane, planeIndex, map));
    }

    /**
     * A constructor
     *
     * @param planes the set of planes that intersect to make the affine space
     * @param y the tip of the cone
     * @param index the index of the last plane in the list of planes in the
     * cone
     * @param map a map that given a key returns the node for that key.
     */
    public ASFail(Plane[] planes, Point y, int index, ConcurrentHashMap<ASKey, ASNode> map) {
        this(ASNode.factory(new AffineSpace(planes).setP(y), planes, index, map));
    }

    /**
     * A plane in the affine space
     *
     * @return a plane containing the affine space.
     */
    private Plane plane() {
        return asNode.plane();
    }

    @Override
    public String toString() {
        return asNode.toString();
    }

    /**
     * Is the projection onto the personal polytope of an immidiate supersapce
     * inside this affine space's personal polytope?
     *
     * @param outPlane the index of the plane the immediate superspace is not
     * contained in.
     * @param lowerFail the projection onto the personal polytope of the
     * immediate superspace.
     * @return true if the projection is inside this affine space's personal
     * polytope, false otherwise.
     */
    private boolean personalPolyContainesSuperProj(int outPlane, Point lowerFail) {
        return lowerFail != null && asNode.planeArray[outPlane].above(lowerFail);
    }

    /**
     * Determines if the underlying affine space is a candidate.
     *
     * @param immidiateSuperSpaces A set of all the affine spaces that are the
     * intersection of one fewer planes than this affine space.
     * @param preProj the point that is being projected.
     * @return true if the underlying affine space is a candidate, and false
     * otherwise.
     */
    public boolean meetsNecesaryCriteria(Map<ASKey, ASFail> immidiateSuperSpaces, Point preProj) {
        if (immidiateSuperSpaces == null)
            return mightContProj = plane().below(preProj);

        if (asNode.as().hasProjFunc()) return mightContProj = true;

        if (asNode.personalPolyHasElement(preProj))
            return mightContProj = false;

        ASKeyRI[] immidiateSuperKeys = asNode.as().immidiateSuperKeys();

        for (ASKeyRI immidiateSuperKey : immidiateSuperKeys) {

            Point projSuperPP = immidiateSuperSpaces.get(immidiateSuperKey).projOntoPersoanlPoly;

            if (personalPolyContainesSuperProj(immidiateSuperKey.removeIndex(), projSuperPP)) {
                projOntoPersoanlPoly = projSuperPP;
                return mightContProj = false;
            }

        }
        return mightContProj = true;
    }

    /**
     * This functions removes the saved projection point. It should be callsed
     * before every new projection.
     */
    public void clearFailure() {
        projOntoPersoanlPoly = null;
    }

}
