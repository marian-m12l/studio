/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';

import './Modal.css';

interface ModalProps {
    id: string;
    title: string;
    content: React.ReactNode;
    buttons: { label: string; onClick: () => void }[];
    onClose: () => void;
    className?: string;
}

const Modal: React.FC<ModalProps> = ({ id, title, content, buttons, onClose, className }) => {
    let classes = 'modal show ';
    if (className) {
        classes += className;
    }

    return (
        <div className={classes} id={`modal-${id}`} tabIndex={-1} role="dialog">
            <div className="modal-dialog modal-dialog-centered" role="document">
                <div className="modal-content">
                    <div className="modal-header">
                        <button type="button" className="close" aria-label="Close" onClick={onClose}>
                            <span aria-hidden="true">&times;</span>
                        </button>
                        <h4 className="modal-title">{title}</h4>
                    </div>
                    <div className="modal-body">
                        {content}
                    </div>
                    <div className="modal-footer">
                        {buttons.map((btn, idx) => (
                            <button key={`modal-button-${idx}`} type="button" className="btn btn-secondary" onClick={btn.onClick}>
                                {btn.label}
                            </button>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Modal;
