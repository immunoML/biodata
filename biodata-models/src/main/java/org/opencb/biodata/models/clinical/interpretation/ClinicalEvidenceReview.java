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

import org.opencb.biodata.models.clinical.ClinicalProperty;

import java.util.ArrayList;
import java.util.List;

import static org.opencb.biodata.models.clinical.ClinicalProperty.ClinicalSignificance;

public class ClinicalEvidenceReview {

    private boolean select;
    private String tier;
    private List<String> acmg;
    private ClinicalProperty.ClinicalSignificance clinicalSignificance;
    private String discussion;

    public ClinicalEvidenceReview() {
        this.select = false;
        this.acmg = new ArrayList<>();
    }

    public ClinicalEvidenceReview(boolean select, String tier, List<String> acmg, ClinicalSignificance clinicalSignificance,
                                  String discussion) {
        this.select = select;
        this.tier = tier;
        this.acmg = acmg;
        this.clinicalSignificance = clinicalSignificance;
        this.discussion = discussion;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ClinicalEvidenceReview{");
        sb.append("select=").append(select);
        sb.append(", tier='").append(tier).append('\'');
        sb.append(", acmg=").append(acmg);
        sb.append(", clinicalSignificance=").append(clinicalSignificance);
        sb.append(", discussion='").append(discussion).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public boolean isSelect() {
        return select;
    }

    public ClinicalEvidenceReview setSelect(boolean select) {
        this.select = select;
        return this;
    }

    public String getTier() {
        return tier;
    }

    public ClinicalEvidenceReview setTier(String tier) {
        this.tier = tier;
        return this;
    }

    public List<String> getAcmg() {
        return acmg;
    }

    public ClinicalEvidenceReview setAcmg(List<String> acmg) {
        this.acmg = acmg;
        return this;
    }

    public ClinicalSignificance getClinicalSignificance() {
        return clinicalSignificance;
    }

    public ClinicalEvidenceReview setClinicalSignificance(ClinicalSignificance clinicalSignificance) {
        this.clinicalSignificance = clinicalSignificance;
        return this;
    }

    public String getDiscussion() {
        return discussion;
    }

    public ClinicalEvidenceReview setDiscussion(String discussion) {
        this.discussion = discussion;
        return this;
    }
}
