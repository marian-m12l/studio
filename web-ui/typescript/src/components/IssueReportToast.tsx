/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React, { useEffect, useState } from 'react';
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

const bugReportTemplate = `**Describe the bug**
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
 - Browser __BROWSER
 - Java version: [e.g. JDK 11.0.6]
 - Maven version: [e.g. 3.6.0]
 - STUdio application version: __APP_VERSION

**Additional context**
Add any other context about the problem here.
`;

interface IssueReportToastProps {
    content: React.ReactNode;
    error?: Error;
}

const IssueReportToast: React.FC<IssueReportToastProps> = ({ content, error }) => {
    const { t } = useTranslation();
    const evergreen = useSelector((state) => state.evergreen);
    const [url, setUrl] = useState('');

    useEffect(() => {
        const errorLog = (error && '> ' + encodeURIComponent(error.message).replace(/(?:(\r\n|\r|\n)\t?|\t)/g, '%0a')) || '';
        const body = encodeURIComponent(bugReportTemplate)
            .replace(/__ISSUE_LOG/g, errorLog)
            .replace(/__OS/g, encodeURIComponent(window.navigator.platform))
            .replace(/__BROWSER/g, encodeURIComponent(window.navigator.userAgent))
            .replace(/__APP_VERSION/g, evergreen.version || 'Unknown');

        setUrl('https://github.com/marian-m12l/studio/issues/new?template=bug_report.md&body=' + body);
    }, [error, evergreen]);

    return (
        <>
            <p>{content}</p>
            <p>
                <a href={url} target="_blank" rel="noopener noreferrer">
                    <span className="glyphicon glyphicon-bell" />{t('toasts.reportIssue')}
                </a>
            </p>
        </>
    );
};

export default IssueReportToast;
