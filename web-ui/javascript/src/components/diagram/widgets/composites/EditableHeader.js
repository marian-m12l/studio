/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import * as SRD from 'storm-react-diagrams';


const EditableHeader = ({node, beingEdited, onToggleEdit, onChange}) => {
    return (
        <div className='title'>
            {node.fromPort && <SRD.DefaultPortLabel model={node.fromPort} />}
            <span className='name'>
                {/* Capture keyUp event to prevent node removal when pressing backspace of delete */}
                {beingEdited ? <input type="text" value={node.name} onChange={onChange} onKeyUp={e => e.stopPropagation()} /> : <strong>{node.name}</strong>}
            </span>
            <span className={'btn btn-xs glyphicon glyphicon-pencil' + (beingEdited ? ' active' : '')}
                  title="Edit node title"
                  onClick={onToggleEdit} />
        </div>
    )
};

export default EditableHeader;