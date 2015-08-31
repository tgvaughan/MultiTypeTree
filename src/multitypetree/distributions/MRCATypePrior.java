package multitypetree.distributions;

import beast.core.Input;
import beast.core.parameter.RealParameter;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.MigrationModel;
import beast.evolution.tree.MultiTypeNode;
import beast.evolution.tree.TreeUtils;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class MRCATypePrior extends MultiTypeTreeDistribution {

    public Input<TaxonSet> taxonSetInput = new Input<>("taxonSet",
            "Set of taxa for which prior information is available.",
            Input.Validate.REQUIRED);

    public Input<RealParameter> typeProbsInput = new Input<>("typeProbs",
            "Parameter specifying individual type probabilities.");

    public Input<String> typeNameInput = new Input<>("typeName",
            "Name of type MRCA is constrained to be.");

    public Input<Integer> typeInput = new Input<>("type",
            "Index of type MRCA is constrained to be.");

    public Input<MigrationModel> migrationModelInput = new Input<>("migrationModel",
            "Migration model");

    protected int type;

    @Override
    public void initAndValidate() throws Exception {
        super.initAndValidate();
        if (typeProbsInput.get() != null
                && typeProbsInput.get().getDimension() != migrationModelInput.get().getNTypes()) {
            throw new IllegalArgumentException("Dimension of type probability" +
                    " parameter must match number of types.");
        } else {
            if (typeNameInput.get() != null) {
                if (!mtTree.getTypeList().contains(typeNameInput.get()))
                    throw new IllegalArgumentException("Type list does not contain" +
                            " type '" + typeNameInput.get() + "'.");
                else
                    type = mtTree.getTypeFromString(typeNameInput.get());
            } else {
                if (typeInput.get() == null)
                    throw new IllegalArgumentException("Must specify typeProbs, " +
                            "typeName or type inputs to MRCATypePrior.");

                if (typeInput.get()<0 || typeInput.get()>=migrationModelInput.get().getNTypes())
                    throw new IllegalArgumentException("Invalid type index " +
                            "specified for type input of MRCATypePrior.");

                type = typeInput.get();
            }
        }
    }

    @Override
    public double calculateLogP() throws Exception {

        MultiTypeNode mrca = (MultiTypeNode)TreeUtils.getCommonAncestorNode(
                mtTree, taxonSetInput.get().getTaxaNames());

        if (typeProbsInput.get() != null)
            logP = Math.log(typeProbsInput.get().getValue(mrca.getNodeType()));
        else
            logP = mrca.getNodeType() == type ? 0.0 : Double.NEGATIVE_INFINITY;

        return logP;
    }


}
