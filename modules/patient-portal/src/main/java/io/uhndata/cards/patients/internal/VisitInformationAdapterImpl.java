/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.patients.internal;

import java.util.Calendar;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.patients.api.VisitInformation;
import io.uhndata.cards.patients.api.VisitInformationAdapter;
import io.uhndata.cards.qsets.api.QuestionnaireRef;
import io.uhndata.cards.qsets.api.QuestionnaireRef.TargetUserType;
import io.uhndata.cards.qsets.api.QuestionnaireSet;
import io.uhndata.cards.qsets.api.QuestionnaireSetUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;
import io.uhndata.cards.subjects.api.SubjectTypeUtils;
import io.uhndata.cards.subjects.api.SubjectUtils;

/**
 * Change listener looking for new or modified forms related to a Visit subject. Initially, when a new Visit Information
 * form is created, it also creates any forms in the specified questionnaire set that need to be created, based on the
 * questionnaire set's specified frequency. When all the forms required for a visit are completed marks in the Visit
 * Information form that the patient has completed the required forms.
 *
 * @version $Id$
 */
@Component
public class VisitInformationAdapterImpl implements VisitInformationAdapter
{
    private static final Logger LOGGER = LoggerFactory.getLogger(VisitInformationAdapterImpl.class);

    /** Provides access to resources. */
    @Reference
    private volatile ResourceResolverFactory resolverFactory;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private QuestionnaireSetUtils questionnaireSetUtils;

    @Reference
    private FormUtils formUtils;

    @Reference
    private SubjectUtils subjectUtils;

    @Reference
    private SubjectTypeUtils subjectTypeUtils;

    @Override
    public VisitInformation toVisitInformation(Node node)
    {
        try {
            if (isVisitInformation(node)) {
                return new VisitInformationImpl(node);
            } else if (isVisitSubject(node)) {
                final Node form = findVisitInformationForm(node);
                if (form != null) {
                    return new VisitInformationImpl(form);
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("Failed to read visit information: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * Check if a questionnaire is the {@code Visit Information} questionnaire.
     *
     * @param questionnaire the questionnaire to check
     * @return {@code true} if the questionnaire is indeed the {@code Visit Information}
     */
    private boolean isVisitInformation(final Node form)
    {
        try {
            return this.formUtils.isForm(form)
                && "/Questionnaires/Visit information".equals(this.formUtils.getQuestionnaire(form).getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check if form is Visit Information Form: {}", e.getMessage(), e);
            return false;
        }
    }

    private boolean isVisitSubject(final Node subject)
    {
        return this.subjectUtils.isSubject(subject)
            && "Visit".equals(this.subjectTypeUtils.getLabel(this.subjectUtils.getType(subject)));
    }

    private Node findVisitInformationForm(final Node subject)
    {
        try {
            for (PropertyIterator i = subject.getReferences("subject"); i.hasNext();) {
                Node form = i.nextProperty().getParent();
                if (isVisitInformation(form)) {
                    return form;
                }
            }
        } catch (final RepositoryException e) {
            // Shouldn't happen
        }
        return null;
    }

    private final class VisitInformationImpl implements VisitInformation
    {
        private final Calendar visitDate;

        private final Node questionnaire;

        private final Node visitInformationForm;

        private final String questionnaireSet;

        private volatile QuestionnaireSet existingForms;

        private volatile QuestionnaireSet templateForms;

        private volatile QuestionnaireSet missingForms;

        private final String clinicPath;

        VisitInformationImpl(final Node visitForm) throws RepositoryException
        {
            Session session = visitForm.getSession();
            this.questionnaire = VisitInformationAdapterImpl.this.formUtils.getQuestionnaire(visitForm);
            this.visitInformationForm = visitForm;

            final Node visitDateQuestion = this.questionnaire.getNode("time");
            this.visitDate = (Calendar) VisitInformationAdapterImpl.this.formUtils
                .getValue(VisitInformationAdapterImpl.this.formUtils.getAnswer(visitForm, visitDateQuestion));

            final Node clinicQuestion =
                VisitInformationAdapterImpl.this.questionnaireUtils.getQuestion(this.questionnaire, "clinic");
            final String clinicName = (String) VisitInformationAdapterImpl.this.formUtils
                .getValue(VisitInformationAdapterImpl.this.formUtils.getAnswer(visitForm, clinicQuestion));
            final Node clinicNode = StringUtils.isNotBlank(clinicName) && session.nodeExists(clinicName)
                ? session.getNode(clinicName) : null;

            // Retrieve the questionnaire set for the specified clinic
            if (clinicNode != null) {
                this.questionnaireSet = session.nodeExists("/Survey/" + clinicNode.getProperty("survey").getString())
                    ? clinicNode.getProperty("survey").getString() : null;
                this.clinicPath = clinicNode.getPath();
            } else {
                this.questionnaireSet = null;
                this.clinicPath = "";
            }
        }

        @Override
        public Node getVisitInformationForm()
        {
            return this.visitInformationForm;
        }

        @Override
        public boolean hasRequiredInformation()
        {
            return this.visitDate != null && StringUtils.isNotBlank(this.questionnaireSet);
        }

        @Override
        public boolean isComplete()
        {
            try {
                if (this.questionnaire.hasNode("surveys_complete")) {
                    Node surveysCompleteQuestion =
                        VisitInformationAdapterImpl.this.questionnaireUtils.getQuestion(this.questionnaire,
                            "surveys_complete");
                    final Long surveysCompleted =
                        (Long) VisitInformationAdapterImpl.this.formUtils
                            .getValue(VisitInformationAdapterImpl.this.formUtils.getAnswer(this.visitInformationForm,
                                surveysCompleteQuestion));
                    if (surveysCompleted != null && surveysCompleted == 1L) {
                        return true;
                    }
                }
            } catch (RepositoryException e) {
                // Ignore for now
            }
            return false;
        }

        @Override
        public boolean isSubmitted()
        {
            try {
                if (this.questionnaire.hasNode("surveys_submitted")) {
                    Node surveysSubmittedQuestion =
                        VisitInformationAdapterImpl.this.questionnaireUtils.getQuestion(this.questionnaire,
                            "surveys_submitted");
                    final Long surveysSubmitted =
                        (Long) VisitInformationAdapterImpl.this.formUtils
                            .getValue(VisitInformationAdapterImpl.this.formUtils.getAnswer(this.visitInformationForm,
                                surveysSubmittedQuestion));
                    if (surveysSubmitted != null && surveysSubmitted == 1L) {
                        return true;
                    }
                }
            } catch (RepositoryException e) {
                // Ignore for now
            }
            return false;
        }

        @Override
        public Calendar getVisitDate()
        {
            return this.visitDate;
        }

        @Override
        public String getQuestionnaireSetName()
        {
            return this.questionnaireSet;
        }

        @Override
        public String getClinicPath()
        {
            return this.clinicPath;
        }

        @Override
        public QuestionnaireSet getTemplateForms()
        {
            if (this.templateForms == null) {
                try {
                    this.templateForms = VisitInformationAdapterImpl.this.questionnaireSetUtils.toQuestionnaireSet(
                        this.questionnaire.getSession().getNode("/Survey/" + getQuestionnaireSetName()),
                        this.getVisitDate());
                } catch (final RepositoryException e) {
                    LOGGER.warn("Failed to retrieve questionnaire set information: {}", e.getMessage(), e);
                }
            }
            return this.templateForms;
        }

        @Override
        public QuestionnaireSet getExistingForms()
        {
            if (this.existingForms == null) {
                try {
                    final QuestionnaireSet template = getTemplateForms();
                    final QuestionnaireSet result =
                        VisitInformationAdapterImpl.this.questionnaireSetUtils.copy(template);
                    result.getQuestionnaires().stream().map(QuestionnaireRef::getQuestionnairePath)
                        .forEach(result::removeQuestionnaire);
                    final Node visitSubject = this.visitInformationForm.getProperty("subject").getNode();
                    for (final PropertyIterator forms = visitSubject.getReferences("subject"); forms.hasNext();) {
                        final Node form = forms.nextProperty().getParent();
                        final Node questionnaireNode =
                            VisitInformationAdapterImpl.this.formUtils.getQuestionnaire(form);
                        if (!form.isSame(this.visitInformationForm)) {
                            final String questionnairePath = questionnaireNode.getPath();
                            if (template.containsQuestionnaire(questionnairePath)) {
                                result.addQuestionnaire(template.getQuestionnaire(questionnairePath));
                            } else {
                                result.addQuestionnaire(VisitInformationAdapterImpl.this.questionnaireSetUtils
                                    .toQuestionnaireRef(questionnaireNode, TargetUserType.ANY, 0));
                            }
                        }
                    }
                    this.existingForms = result;
                } catch (final RepositoryException e) {
                    LOGGER.warn("Failed to compile list of existing forms: {}", e.getMessage(), e);
                }
            }
            return this.existingForms;
        }

        @Override
        public QuestionnaireSet getMissingForms()
        {
            if (this.missingForms == null) {
                try {
                    final QuestionnaireSet result =
                        VisitInformationAdapterImpl.this.questionnaireSetUtils.copy(this.getTemplateForms());
                    for (QuestionnaireRef existing : this.getExistingForms().getQuestionnaires()) {
                        result.removeQuestionnaire(existing.getQuestionnairePath());
                    }
                    final Node visitNode = this.visitInformationForm.getProperty("subject").getNode();
                    final Node patientNode = visitNode.getParent();
                    for (final NodeIterator visits = patientNode.getNodes(); visits.hasNext();) {
                        final Node otherVisit = visits.nextNode();
                        // If the visit is the triggering visit, ignore it as the triggering visit has already been
                        // checked.
                        if (!visitNode.isSame(otherVisit)
                            && VisitInformationAdapterImpl.this.subjectUtils.isSubject(otherVisit)
                            && "Visit".equals(VisitInformationAdapterImpl.this.subjectTypeUtils
                                .getLabel(VisitInformationAdapterImpl.this.subjectUtils.getType(otherVisit)))) {
                            final VisitInformation otherVI = toVisitInformation(otherVisit);
                            if (otherVI != null) {
                                result.pruneConflicts(otherVI.getExistingForms());
                            }
                        }
                        if (result.isEmpty()) {
                            break;
                        }
                    }
                    this.missingForms = result;
                } catch (final RepositoryException e) {
                    LOGGER.warn("Failed to compile list of needed forms: {}", e.getMessage(), e);
                }
            }
            return this.missingForms;
        }
    }
}
