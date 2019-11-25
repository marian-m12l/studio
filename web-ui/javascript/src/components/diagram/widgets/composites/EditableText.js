/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import { useTranslation } from 'react-i18next';


const EditableText = ({value, onChange}) => {

    const [beingEdited, setBeingEdited] = React.useState(false);

    const onClick = (e) => setBeingEdited(true);
    const onKeyUp = (e) => {
        e.stopPropagation();
        if (e.key === 'Enter' || e.key === 'Escape') {
            setBeingEdited(false);
        }
    };
    const onBlur = (e) => setBeingEdited(false);

    const { t } = useTranslation();

    return beingEdited
        ? <input type="text"
                 autoFocus={true}
                 className={'editable-text-input'}
                 value={value}
                 onChange={onChange}
                 onKeyUp={onKeyUp}
                 onBlur={onBlur} />
        : <span title={t('editor.diagram.editText')}
                className={'editable-text'}
                onClick={onClick}>
            {value}
        </span>;
};

export default EditableText;