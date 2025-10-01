/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import {withTranslation} from "react-i18next";
import {connect} from "react-redux";


const bugReportTemplate = {
    en: `


> [!WARNING]
> **:warning: DO NOT SUBMIT THIS ISSUE TEMPLATE AS-IS :warning:**
> 
> Please follow the template and replace/fill each section with appropriate information.

> [!WARNING]
> **:warning: NE PAS SOUMETTRE LE RAPPORT D'ANOMALIE TEL QUEL :warning:**
> 
> Merci de suivre le modèle et de remplacer/compléter chaque section avec les informations nécessaires. 


**Describe the bug**
A clear and concise description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'
4. See error

**Expected behavior**
A clear and concise description of what you expected to happen.

**Screenshots**
If applicable, add screenshots to help explain your problem.

**Logs**
Check the logs **in the console** for errors, if any. Join the \`studio-latest.log\` file if relevant.
Also check **the network tab of your browser's developer tools** and look for HTTP calls in error, if any. HTTP responses may contain useful data.

__ISSUE_LOG

**Desktop and environment (please complete the following information):**
 - OS: __OS
 - Browser: __BROWSER
 - Java version: [e.g. JDK 11.0.6]
 - Maven version: [e.g. 3.6.0]
 - STUdio application version: __APP_VERSION

**Additional context**
Add any other context about the problem here.
`,

    fr: `


> [!WARNING]
> **:warning: NE PAS SOUMETTRE LE RAPPORT D'ANOMALIE TEL QUEL :warning:**
> 
> Merci de suivre le modèle et de remplacer/compléter chaque section avec les informations nécessaires. 

> [!WARNING]
> **:warning: DO NOT SUBMIT THIS ISSUE TEMPLATE AS-IS :warning:**
> 
> Please follow the template and replace/fill each section with appropriate information.


**Description de l'anomalie**
Une description claire et concise de l'anomalie.

**Comment reproduire**
Étapes pour reproduire le comportement constaté (par exemple) :
1. Aller sur '...'
2. Cliquer sur '....'
3. Defiler jusqu'à '....'
4. Une erreur s'affiche avec le message '...'

**Comportement attendu**
Une description claire et concise du comportement que vous attendez de l'application.

**Captures d'écran**
Si possible, joindre des captures d'écran mettant en avant le problème.

**Logs**
Vérifiez la présence d'erreurs **dans la console**. Joindre le fichier \`studio-latest.log\` si nécessaire.
Vérifier également **l'onglet réseau des outils de développement du navigateur** et vérifier la présence d'erreurs HTTP. Les réponses HTTP peuvent contenir des informations utiles.

__ISSUE_LOG

**Configuration de l'environnement (à compléter):**
 - OS : __OS
 - Navigateur : __BROWSER
 - Version de Java : [e.g. JDK 11.0.6]
 - Version de Maven : [e.g. 3.6.0]
 - Version de STUdio : __APP_VERSION

**Autre**
Ajouter toute information ou contexte utile à l'identification et à la résolution du problème.
`
};

class IssueReportToast extends React.Component {

    render() {
        const { t, i18n } = this.props;

        // Fill environment and error log
        let errorLog = (this.props.error && '> ' + encodeURIComponent(this.props.error.message).replace(/(?:(\r\n|\r|\n)\t?|\t)/g, '%0a')) || '';
        let body = encodeURIComponent(bugReportTemplate[i18n.language])
            .replace(/__ISSUE_LOG/g, errorLog)
            .replace(/__OS/g, encodeURIComponent(window.navigator.platform))
            .replace(/__BROWSER/g, encodeURIComponent(window.navigator.userAgent))
            .replace(/__APP_VERSION/g, this.props.evergreen.version || 'Unknown');

        this.url = `https://github.com/marian-m12l/studio/issues/new?template=bug_report_${i18n.language}.md&body=${body}`;

        return (
            <>
                <p>{this.props.content}</p>
                <p>
                    <a href={this.url} target="_blank" rel="noopener noreferrer"><span className="glyphicon glyphicon-bell"/>{t('toasts.reportIssue')}</a>
                </p>
            </>
        );
    }
}

IssueReportToast.propTypes = {
    content: PropTypes.element.isRequired,
    error: PropTypes.instanceOf(Error)
};

const mapStateToProps = (state, ownProps) => ({
    evergreen: state.evergreen
});

const mapDispatchToProps = (dispatch, ownProps) => ({
});

export default withTranslation()(
    connect(
        mapStateToProps,
        mapDispatchToProps
    )(IssueReportToast)
);
