package org.opencb.biodata.tools.clinical;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.opencb.biodata.models.clinical.interpretation.DiseasePanel;
import org.opencb.biodata.models.clinical.interpretation.ReportedEvent;
import org.opencb.biodata.models.clinical.interpretation.ReportedVariant;
import org.opencb.biodata.models.clinical.interpretation.VariantClassification;
import org.opencb.commons.datastore.core.ObjectMap;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultTieringCalculator extends TieringCalculator {

    private List<DiseasePanel> diseasePanels;
    private Map<String, Set<DiseasePanel>> variantToPanel;
    private Map<String, Set<DiseasePanel>> geneToPanel;

    public DefaultTieringCalculator(ObjectMap config) {
        // Panel management
        if (CollectionUtils.isNotEmpty(config.getAsList(ClinicalUtils.PANELS))) {
            diseasePanels = (List<DiseasePanel>) config.get(ClinicalUtils.PANELS);
            variantToPanel = ClinicalUtils.getVariantToPanelMap(diseasePanels);
            geneToPanel = ClinicalUtils.getGeneToPanelMap(diseasePanels);
        }
    }

    @Override
    public void setTier(ReportedVariant reportedVariant) {
        // Sanity check
        if (reportedVariant == null) {
            return;
        }

        if (CollectionUtils.isNotEmpty(reportedVariant.getEvidences())) {
            for (ReportedEvent reportedEvent : reportedVariant.getEvidences()) {
                if (StringUtils.isNotEmpty(reportedEvent.getPanelId())) {
                    // Reported event with panel by variant, region or gene ?
                    if (variantToPanel.containsKey(reportedVariant.toStringSimple())) {
                        // TIER 1
                        reportedEvent.getClassification().setTier(VariantClassification.TIER_1);
                    } else if (geneToPanel.containsKey(reportedEvent.getGenomicFeature().getId())) {
                        // TIER 2
                        reportedEvent.getClassification().setTier(VariantClassification.TIER_2);
                    } else {
                        // TIER 3
                        reportedEvent.getClassification().setTier(VariantClassification.TIER_3);
                    }
                } else {
                    // Reported event without panel
                    reportedEvent.getClassification().setTier(VariantClassification.UNTIERED);
                }
            }
        }
    }
}
