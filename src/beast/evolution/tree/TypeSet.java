package beast.evolution.tree;

import beast.core.BEASTObject;
import beast.core.Input;

import java.util.*;

/**
 * Ordered set of type names.
 *
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class TypeSet extends BEASTObject {

    public Input<String> valueInput = new Input<>("value", "Comma-delmited list of types.");
    public Input<TraitSet> typeTraitSetInput = new Input<>("typeTraitSet", "Type trait set defining list of types.");

    protected SortedSet<String> typeNameSet = new TreeSet<>();

    public TypeSet() {}

    /**
     * Constructor to produce type set containing types provided as arguments.
     * Useful for testing.
     *
     * @param typeNames varargs array of type names to include
     */
    public TypeSet(String ... typeNames) {
        typeNameSet.addAll(Arrays.asList(typeNames));
    }

    @Override
    public void initAndValidate() {
        typeNameSet.clear();

        if (valueInput.get() != null) {
            for (String typeName : valueInput.get().split(","))
                if (!typeName.isEmpty())
                    typeNameSet.add(typeName);
        }

        if (typeTraitSetInput.get() != null)
            addTypesFromTypeTraitSet(typeTraitSetInput.get());
    }

    /**
     * Incorporates all of the traits present in the given trait set into the type set.
     *
     * @param typeTraitSet
     */
    public void addTypesFromTypeTraitSet(TraitSet typeTraitSet) {
        typeNameSet.addAll(Arrays.asList(typeTraitSet.taxonValues));
    }

    /**
     * @return the number of unique types defined in this type set
     */
    public int getNTypes() {
        return Math.max(typeNameSet.size(), 2);
    }

    /**
     * @param typeName name of type
     * @return numerical index representing type
     */
    public int getTypeIndex(String typeName) {
        if (typeNameSet.contains(typeName))
            return (new ArrayList<>(typeNameSet).indexOf(typeName));
        else
            throw new IllegalArgumentException("TypeSet does not contain type with name " + typeName);
    }

    /**
     * @param typeIdx numerical index representing type
     * @return name of type
     */
    public String getTypeName(int typeIdx) {
        if (typeIdx<typeNameSet.size())
            return (new ArrayList<>(typeNameSet).get(typeIdx));
        else
            return "type_" + typeIdx;
    }

    /**
     * @param typeName name of type
     * @return true iff this TypeSet contains a type with name typeName.
     */
    public boolean containsTypeWithName(String typeName) {
        return typeNameSet.contains(typeName);
    }

    /**
     * @return list of type names ordered according to type index
     */
    public List<String> getTypesAsList() {
        return new ArrayList<>(typeNameSet);
    }


}
