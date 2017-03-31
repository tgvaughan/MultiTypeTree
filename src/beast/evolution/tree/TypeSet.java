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
    public Input<TraitSet> typeTraitSet = new Input<>("typeTraitSet", "Type trait set defining list of types.");

    protected SortedSet<String> typeNameSet = new TreeSet<>();

    @Override
    public void initAndValidate() {
        typeNameSet.clear();

        if (valueInput.get() != null)
            typeNameSet.addAll(Arrays.asList(valueInput.get().split(",")));

        if (typeTraitSet.get() != null)
            typeNameSet.addAll(Arrays.asList(typeTraitSet.get().taxonValues));
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
     * @return list of type names ordered according to type index
     */
    public List<String> getTypesAsList() {
        return new ArrayList<>(typeNameSet);
    }


}
