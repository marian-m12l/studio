/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";

import './Modal.css';


class Modal extends React.Component {

    render() {
        let classes = 'modal show ';
        if (this.props.className) {
            classes += this.props.className
        }
        return (
            <div className={classes} id={`modal-${this.props.id}`} tabIndex="-1" role="dialog">
                <div className="modal-dialog modal-dialog-centered" role="document">
                    <div className="modal-content">
                        <div className="modal-header">
                            <button type="button" className="close" aria-label="Close" onClick={this.props.onClose}>
                                <span aria-hidden="true">&times;</span>
                            </button>
                            <h4 className="modal-title">{this.props.title}</h4>
                        </div>
                        <div className="modal-body">
                            {this.props.content}
                        </div>
                        <div className="modal-footer">
                            {this.props.buttons.map((btn,idx) => <button key={`modal-button-${idx}`} type="button" className="btn btn-secondary" onClick={btn.onClick}>{btn.label}</button>)}
                        </div>
                    </div>
                </div>
            </div>
        );
    }
}

Modal.propTypes = {
    id: PropTypes.string.isRequired,
    title: PropTypes.string.isRequired,
    content: PropTypes.element.isRequired,
    buttons: PropTypes.array.isRequired,
    onClose: PropTypes.func.isRequired,
    className: PropTypes.string
};

export default Modal;
