/*
 * <!--
 *   ~ Copyright 2015-2017 OpenCB
 *   ~
 *   ~ Licensed under the Apache License, Version 2.0 (the "License");
 *   ~ you may not use this file except in compliance with the License.
 *   ~ You may obtain a copy of the License at
 *   ~
 *   ~     http://www.apache.org/licenses/LICENSE-2.0
 *   ~
 *   ~ Unless required by applicable law or agreed to in writing, software
 *   ~ distributed under the License is distributed on an "AS IS" BASIS,
 *   ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   ~ See the License for the specific language governing permissions and
 *   ~ limitations under the License.
 *   -->
 *
 */

package org.opencb.biodata.models.clinical.interpretation;

import org.apache.commons.collections.CollectionUtils;
import org.opencb.biodata.models.variant.avro.*;
import org.opencb.commons.utils.ListUtils;

import java.util.*;

import static org.opencb.biodata.models.variant.avro.ClinicalSignificance.*;

public class VariantClassification {

    public static final Set<String> LOF = new HashSet<>(Arrays.asList("transcript_ablation", "splice_acceptor_variant", "splice_donor_variant",
            "stop_gained", "frameshift_variant", "stop_lost", "start_lost", "transcript_amplification", "inframe_insertion",
            "inframe_deletion"));

    public static final Set<String> PROTEIN_LENGTH_CHANGING = new HashSet<>(Arrays.asList("stop_gained", "stop_lost", "frameshift_variant",
            "inframe_insertion", "inframe_deletion", "splice_acceptor_variant", "splice_donor_variant"));

    public static final String TIER_1 = "Tier1";
    public static final String TIER_2 = "Tier2";
    public static final String TIER_3 = "Tier3";
    public static final String UNTIERED = "none";

    private String tier;
    private List<String> acmg;
    private ClinicalSignificance clinicalSignificance;
    private DrugResponse drugResponse;
    private TraitAssociation traitAssociation;
    private FunctionalEffect functionalEffect;
    private Tumorigenesis tumorigenesis;
    private List<String> other;

    public enum ClinicalSignificance {
        PATHOGENIC_VARIANT,
        LIKELY_PATHOGENIC_VARIANT,
        VARIANT_OF_UNKNOWN_CLINICAL_SIGNIFICANCE,
        LINKELY_BENIGN_VARIANT,
        BENIGN_VARIANT,
        NOT_ASSESSED
    }

    public enum DrugResponse {
        ALTERED_SENSITIVITY,
        REDUCED_SENSITIVITY,
        INCREASED_SENSITIVITY,
        ALTERED_RESISTANCE,
        INCREASED_RESISTANCE,
        REDUCED_RESISTANCE,
        INCREASED_RISK_OF_TOXICITY,
        REDUCED_RISK_OF_TOXICITY,
        ALTERED_TOXICITY,
        ADVERSE_DRUG_REACTION,
        INDICATION,
        CONTRAINDICATION,
        DOSING_ALTERATION,
        INCREASED_DOSE,
        REDUCED_DOSE,
        INCREASED_MONITORING,
        INCREASED_EFFICACY,
        REDUCED_EFFICACY,
        ALTERED_EFFICACY
    }

    public enum TraitAssociation {
        ESTABLISHED_RISK_ALLELE,
        LIKELY_RISK_ALLELE,
        UNCERTAIN_RISK_ALLELE,
        PROTECTIVE
    }

    public enum FunctionalEffect {
        DOMINANT_NEGATIVE_VARIANT,
        GAIN_OF_FUNCTION_VARIANT,
        LETHAL_VARIANT,
        LOSS_OF_FUNCTION_VARIANT,
        LOSS_OF_HETEROZYGOSITY,
        NULL_VARIANT
    }

    public enum Tumorigenesis {
        DRIVER,
        PASSENGER,
        MODIFIER
    }

    public static List<String> calculateAcmgClassification(ConsequenceType consequenceType, VariantAnnotation annotation) {
        return calculateAcmgClassification(consequenceType, annotation, null);
    }

    public static List<String> calculateAcmgClassification(ConsequenceType consequenceType, VariantAnnotation annotation,
                                                           ClinicalProperty.ModeOfInheritance moi) {
        Set<String> acmg = new HashSet<>();

        // TODO: PM1
        //   Manual: PS3, PS4, PM3
        //   ?? PM6, PP1 (Cosegregation),

        for (SequenceOntologyTerm so: consequenceType.getSequenceOntologyTerms()) {
            // PVS1
            if (LOF.contains(so.getName())) {
                acmg.add("PVS1");
            }

            // PS1, BP7 (dependes on annotation)
            if (annotation != null && "synonymous_variant".equals(so.getName())) {
                for (EvidenceEntry evidenceEntry : annotation.getTraitAssociation()) {
                    if ("clinvar".equals(evidenceEntry.getSource().getName())
                            && (evidenceEntry.getVariantClassification().getClinicalSignificance() == pathogenic
                            || evidenceEntry.getVariantClassification().getClinicalSignificance() == likely_pathogenic)) {
                        acmg.add("PS1");
                    } else {
                        acmg.add("BP7");
                    }
                }
            }

            // PM4
            if (PROTEIN_LENGTH_CHANGING.contains(so.getName()) && "protein_coding".equals(consequenceType.getBiotype())) {
                acmg.add("PM4");
            }
        }

        // PM5 or PP2
//                if ("missense_variant".equals(so.getName())) {
//                    acmg.add("PM5");
//                }
        //  PP3, BP4
        if (consequenceType.getProteinVariantAnnotation() != null
                && ListUtils.isNotEmpty(consequenceType.getProteinVariantAnnotation().getSubstitutionScores())
                && ListUtils.isNotEmpty(annotation.getFunctionalScore())
                && ListUtils.isNotEmpty(annotation.getConservation())) {
            double sift = Double.MIN_VALUE;
            double polyphen = Double.MIN_VALUE;
            double scaledCadd = Double.MIN_VALUE;
            double gerp = Double.MIN_VALUE;
            for (Score score: consequenceType.getProteinVariantAnnotation().getSubstitutionScores()) {
                switch (score.getSource()) {
                    case "sift":
                        sift = score.getScore();
                        break;
                    case "polyphen":
                        polyphen = score.getScore();
                        break;
                }
            }
            for (Score score: annotation.getFunctionalScore()) {
                if ("cadd_scaled".equals(score.getSource())) {
                    scaledCadd = score.getScore();
                    break;
                }
            }
            for (Score score: annotation.getConservation()) {
                if ("gerp".equals(score.getSource())) {
                    gerp = score.getScore();
                    break;
                }
            }

            if (sift != Double.MIN_VALUE && polyphen != Double.MIN_VALUE && scaledCadd != Double.MIN_VALUE
                    && gerp != Double.MIN_VALUE) {
                if (sift < 0.05 && polyphen > 0.91 && scaledCadd > 15 && gerp > 2) {
                    acmg.add("PP3");
                } else {
                    acmg.add("BP4");
                }
            }
        }

        if (moi != null) {
            if (moi == ClinicalProperty.ModeOfInheritance.DE_NOVO) {
                acmg.add("PS2");
            } else if (moi == ClinicalProperty.ModeOfInheritance.COMPOUND_HETEROZYGOUS) {
                acmg.add("PM3");
            }
        }

        // PM2, BA1
        if (annotation != null) {
            if (ListUtils.isEmpty(annotation.getPopulationFrequencies())) {
                acmg.add("PM2");
            } else {
                boolean above5 = false;
                boolean hasPopFreq = false;
                for (PopulationFrequency populationFrequency : annotation.getPopulationFrequencies()) {
                    // TODO: check it!
                    if (populationFrequency.getAltAlleleFreq() != 0) {
                        hasPopFreq = true;
                    }
                    if ("EXAC".equals(populationFrequency.getStudy())
                            || "1kG_phase3".equals(populationFrequency.getStudy())
                            || "GNOMAD_EXOMES".equals(populationFrequency.getStudy())) {
                        if (populationFrequency.getAltAlleleFreq() > 0.05) {
                            above5 = true;
                        }
                    }
                    if (hasPopFreq && above5) {
                        break;
                    }
                }
                if (!hasPopFreq) {
                    acmg.add("PM2");
                }
                if (above5) {
                    acmg.add("BA1");
                }
            }

            if (annotation.getTraitAssociation() != null) {
                for (EvidenceEntry evidenceEntry : annotation.getTraitAssociation()) {
                    if ("clinvar".equals(evidenceEntry.getSource().getName())
                            && (evidenceEntry.getVariantClassification().getClinicalSignificance() == benign
                            || evidenceEntry.getVariantClassification().getClinicalSignificance() == likely_benign)) {
                        acmg.add("BP6");
                    } else if ("clinvar".equals(evidenceEntry.getSource().getName())
                            && (evidenceEntry.getVariantClassification().getClinicalSignificance() == pathogenic
                            || evidenceEntry.getVariantClassification().getClinicalSignificance() == likely_pathogenic)) {
                        acmg.add("PP5");
                    }
                }
            }
        }

        return new ArrayList<>(acmg);
    }

//    public static VariantClassification.ClinicalSignificance computeClinicalSignificance(Variant variant, List<DiseasePanel> panels) {
//        if (CollectionUtils.isNotEmpty(panels)) {
//            for (DiseasePanel panel : panels) {
//                if (CollectionUtils.isNotEmpty(panel.getVariants())) {
//                    for (DiseasePanel.VariantPanel panelVariant : panel.getVariants()) {
//                        if (variant.getId().equals(panelVariant.getId())) {
//                            return ClinicalSignificance.PATHOGENIC_VARIANT;
//                        }
//                    }
//                }
//            }
//        }
//
//        return computeClinicalSignificance(calculateAcmgClassification(variant));
//    }

    public static VariantClassification.ClinicalSignificance computeClinicalSignificance(List<String> acmgs) {
        if (CollectionUtils.isEmpty(acmgs)) {
            return ClinicalSignificance.VARIANT_OF_UNKNOWN_CLINICAL_SIGNIFICANCE;
        }

        List<String> prefixes = Arrays.asList("PVS,PS,PP,PM,BS,BP,BA".split(","));
        Map<String, Integer> acmgCounter = new HashMap<>();
        for (String prefix : prefixes) {
            acmgCounter.put(prefix, 0);
        }

        for (String acmg : acmgs) {
            String prefix = acmg.split("[1-9]")[0];
            acmgCounter.put(prefix, acmgCounter.get(prefix) + 1);
        }

        // ACMG rules for clinical significance
        if ((acmgCounter.get("PVS") > 0 && (acmgCounter.get("PS") >= 1 || acmgCounter.get("PM") >= 2 || (acmgCounter.get("PM") == 1
                && acmgCounter.get("PP") == 1) || acmgCounter.get("PP") >= 2)
                ||
                (acmgCounter.get("PS") >= 2)
                ||
                (acmgCounter.get("PS") == 1 && (acmgCounter.get("PM") >=3 || (acmgCounter.get("PM") >= 2 && acmgCounter.get("PP") >= 2)
                        || (acmgCounter.get("PM") == 1 && acmgCounter.get("PP") >= 4))))) {
            return ClinicalSignificance.PATHOGENIC_VARIANT;
        } else if ((acmgCounter.get("PVS") == 1 && acmgCounter.get("PM") == 1)
                ||
                (acmgCounter.get("PS") == 1 && acmgCounter.get("PM") >= 1)
                ||
                (acmgCounter.get("PS") == 1 && acmgCounter.get("PP") >= 2)
                ||
                (acmgCounter.get("PM") >= 3)
                ||
                (acmgCounter.get("PM") == 2 && acmgCounter.get("PP") >= 2)
                ||
                (acmgCounter.get("PM") == 1 && acmgCounter.get("PP") >= 4)) {
            return ClinicalSignificance.LIKELY_PATHOGENIC_VARIANT;
        } else if (acmgCounter.get("BA") == 1 || acmgCounter.get("BS") >= 2) {
            return  ClinicalSignificance.BENIGN_VARIANT;
        } else if ((acmgCounter.get("BS") == 1 && acmgCounter.get("BP") == 1) || (acmgCounter.get("BP") >= 2)) {
            return ClinicalSignificance.LINKELY_BENIGN_VARIANT;
        }
        return ClinicalSignificance.VARIANT_OF_UNKNOWN_CLINICAL_SIGNIFICANCE;
    }

    public VariantClassification() {
        this.tier = UNTIERED;
        this.acmg = new ArrayList<>();
    }

    public VariantClassification(String tier, List<String> acmg, ClinicalSignificance clinicalSignificance, DrugResponse drugResponse,
                                 TraitAssociation traitAssociation, FunctionalEffect functionalEffect, Tumorigenesis tumorigenesis, List<String> other) {
        this.tier = tier;
        this.acmg = acmg;
        this.clinicalSignificance = clinicalSignificance;
        this.drugResponse = drugResponse;
        this.traitAssociation = traitAssociation;
        this.functionalEffect = functionalEffect;
        this.tumorigenesis = tumorigenesis;
        this.other = other;
    }

    public String getTier() {
        return tier;
    }

    public VariantClassification setTier(String tier) {
        this.tier = tier;
        return this;
    }

    public List<String> getAcmg() {
        return acmg;
    }

    public VariantClassification setAcmg(List<String> acmg) {
        this.acmg = acmg;
        return this;
    }

    public ClinicalSignificance getClinicalSignificance() {
        return clinicalSignificance;
    }

    public VariantClassification setClinicalSignificance(ClinicalSignificance clinicalSignificance) {
        this.clinicalSignificance = clinicalSignificance;
        return this;
    }

    public DrugResponse getDrugResponse() {
        return drugResponse;
    }

    public VariantClassification setDrugResponse(DrugResponse drugResponse) {
        this.drugResponse = drugResponse;
        return this;
    }

    public TraitAssociation getTraitAssociation() {
        return traitAssociation;
    }

    public VariantClassification setTraitAssociation(TraitAssociation traitAssociation) {
        this.traitAssociation = traitAssociation;
        return this;
    }

    public FunctionalEffect getFunctionalEffect() {
        return functionalEffect;
    }

    public VariantClassification setFunctionalEffect(FunctionalEffect functionalEffect) {
        this.functionalEffect = functionalEffect;
        return this;
    }

    public Tumorigenesis getTumorigenesis() {
        return tumorigenesis;
    }

    public VariantClassification setTumorigenesis(Tumorigenesis tumorigenesis) {
        this.tumorigenesis = tumorigenesis;
        return this;
    }

    public List<String> getOther() {
        return other;
    }

    public VariantClassification setOther(List<String> other) {
        this.other = other;
        return this;
    }

    public static Set<String> getLOF() {
        return LOF;
    }

    public static Set<String> getProteinLengthChanging() {
        return PROTEIN_LENGTH_CHANGING;
    }
}
