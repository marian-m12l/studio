/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import {toast} from "react-toastify";
import {withTranslation} from "react-i18next";
import { DiagramEngine, NodeModel, LinkModel } from '@projectstorm/react-diagrams';
import { CanvasWidget } from '@projectstorm/react-canvas-core';
import {connect} from "react-redux";
import Switch from "react-switch";

import StageNodeModel from "../models/StageNodeModel";
import ActionNodeModel from "../models/ActionNodeModel";
import CoverNodeModel from "../models/CoverNodeModel";
import MenuNodeModel from "../models/MenuNodeModel";
import StoryNodeModel from "../models/StoryNodeModel";
import TrayWidget from "./TrayWidget";
import TrayItemWidget from "./TrayItemWidget";
import Modal from "../../Modal";
import {setEditorDiagram, setEditorFilename, setDiagramErrors} from "../../../actions";
import {generateFilename} from "../../../utils/packs";


class PackDiagramWidget extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            editor: null,
            showFilenameSuggestionDialog: false,
            displayForwardWorkflow: true,
            displayReturningWorkflow: true,
            showHelpDialog: null
        }
    }

    componentDidMount() {
        if (this.props.editor) {
            this.setState({
                editor: this.props.editor
            });
        }
    }

    componentWillReceiveProps(nextProps, nextContext) {
        if (nextProps.editor && nextProps.editor !== this.state.editor) {
            this.setState({
                editor: this.props.editor
            });
        }
    }

    getDiagramModel = () => {
        return (this.props.diagramEngine.getModel() || {});
    };

    changeTitle = (e) => {
        this.props.diagramEngine.getModel().title = e.target.value;
        this.forceUpdate();
    };

    changeVersion = (e) => {
        this.props.diagramEngine.getModel().version = parseInt(e.target.value);
        this.forceUpdate();
    };

    checkFilename = (e) => {
        this.onInputBlur(e);
        if (this.props.editor.filename !== generateFilename(this.props.diagramEngine.getModel())) {
            this.showFilenameSuggestionDialog();
        }
    };

    changeFilename = (e) => {
        this.props.setEditorFilename(e.target.value);
    };

    useSuggestedFilename = () => {
        // Renew pack/entry-point uuid (this is used as the key in metadata database)
        let diagram = this.props.diagramEngine.getModel();
        diagram.getEntryPoint().renewUuid();
        this.props.setEditorDiagram(diagram, generateFilename(diagram));
        this.dismissFilenameSuggestionDialog();
    };

    changeDescription = (e) => {
        this.props.diagramEngine.getModel().description = e.target.value;
        this.forceUpdate();
    };

    changeNightModeAvailable = (nightModeAvailable) => {
        this.props.diagramEngine.getModel().nightModeAvailable = nightModeAvailable;
        this.forceUpdate();
    };

    onInputFocus = (e) => {
        // Lock model when typing text to avoid deletion of selection
        this.props.diagramEngine.getModel().setLocked(true);
    };

    onInputBlur = (e) => {
        this.props.diagramEngine.getModel().setLocked(false);
    };

    showThumbnailSelector = () => {
        document.getElementById('pack-thumb').click();
    };

    changeThumbnail = (event) => {
        const { t } = this.props;
        let file = event.target.files[0];
        if (!file) {
            return;
        }
        if (file.type !== 'image/png') {
            toast.error(t('toasts.editor.thumbWrongType'));
            return;
        }
        let reader = new FileReader();
        let that = this;
        reader.addEventListener("load", () => {
            console.log(reader.result);
            that.props.diagramEngine.getModel().thumbnail = reader.result;
            that.forceUpdate();
        }, false);
        reader.readAsDataURL(file);
    };

    showFilenameSuggestionDialog = () => {
        this.setState({showFilenameSuggestionDialog: true});
    };

    dismissFilenameSuggestionDialog = () => {
        this.setState({showFilenameSuggestionDialog: false});
    };

    onDropNode = (event) => {
        event.preventDefault();
        const { t } = this.props;
        let nodeData = event.dataTransfer.getData("storm-diagram-node");
        if (!nodeData) {
            // Ignore missing node data
            return;
        }
        var data = JSON.parse(nodeData);
        var node = null;
        switch (data.type) {
            case "stage":
                node = new StageNodeModel({ name: "Stage node" });
                // Make it the "square one" if there are no entry point in the diagram
                if (!this.props.diagramEngine.getModel().getEntryPoint()) {
                    node.setSquareOne(true);
                }
                break;
            case "action":
                // One option by default
                node = new ActionNodeModel({ name: "Action node" });
                node.addOption();
                break;
            case "cover":
                // Make sure there is only one entry point per diagram
                if (this.props.diagramEngine.getModel().getEntryPoint()) {
                    toast.error(t('toasts.editor.tooManyEntryPoints'));
                    return;
                }
                node = new CoverNodeModel({ name: "Cover node" });
                break;
            case "menu":
                // Two options by default
                node = new MenuNodeModel({ name: "Menu node" });
                node.addOption();
                node.addOption();
                break;
            case "story":
                node = new StoryNodeModel({ name: "Story node" });
                break;
            default:
                // Unsupported node
                break;
        }
        var points = this.props.diagramEngine.getRelativeMousePoint(event);
        node.setPosition(points.x, points.y);
        this.props.diagramEngine.getModel().addNode(node);
        this.forceUpdate();
    };

    zoomToFit = () => {
        this.props.diagramEngine.zoomToFit();
    };

    cloneSelection = () => {
        const { t } = this.props;
        let engine = this.props.diagramEngine;
        let offset = { x: 100, y: 100 };
        let model = engine.getModel();

        // Cannot clone the cover node
        if (model.getSelectedEntities().find(item => item instanceof CoverNodeModel)) {
            toast.error(t('toasts.editor.cannotCloneCoverNode'));
            return;
        }

        let itemMap = {};

        // First, clone all nodes (and their ports)
        model.getSelectedEntities().filter(item => item instanceof NodeModel).forEach(item => {
            let newItem = item.clone(itemMap);

            // Unselect old items and select new items
            item.setSelected(false);
            newItem.setSelected(true);

            // Offset the nodes slightly
                newItem.setPosition(newItem.getX() + offset.x, newItem.getY() + offset.y);
                model.addNode(newItem);
        });

        // Next, clone links (because ports are automatically renamed when cloned, links must be cloned afterwards to avoid corrupting ports map on node models)
        model.getSelectedEntities().filter(item => item instanceof LinkModel).forEach(item => {
            let newItem = item.clone(itemMap);

            // Unselect old items and select new items
            item.setSelected(false);
            newItem.setSelected(true);

            // Offset the link points
            newItem.getPoints().forEach(p => {
                p.setPosition(p.getX() + offset.x, p.getY() + offset.y);
            });
            model.addLink(newItem);
        });

        this.forceUpdate();
    };

    toggleDisplayForwardWorkflow = () => {
        this.setState({displayForwardWorkflow: !this.state.displayForwardWorkflow});
    };

    toggleDisplayReturningWorkflow = () => {
        this.setState({displayReturningWorkflow: !this.state.displayReturningWorkflow});
    };

    verifyDiagram = () => {
        const { t } = this.props;
        console.log('Verifying diagram...');
        let engine = this.props.diagramEngine;
        let model = engine.getModel();

        let errors = {};
        let actionNodesCount = 0;

        // Verify all nodes
        model.getNodes().forEach(node => {
            console.log('Verifying node... ' + node.getID());
            if (node instanceof StageNodeModel || node instanceof CoverNodeModel || node instanceof StoryNodeModel) {
                if (node.fromPort && Object.keys(node.fromPort.getLinks()).length < 1) {
                    console.log('Missing link on FROM port: ' + node.fromPort.getName());
                    if (!errors[node.getID()]) {
                        errors[node.getID()] = {};
                    }
                    errors[node.getID()].fromPort = t('editor.verify.errors.fromPort');
                }
                if (node.okPort && Object.keys(node.okPort.getLinks()).length !== 1) {
                    console.log('Missing link on OK port: ' + node.okPort.getName());
                    if (!errors[node.getID()]) {
                        errors[node.getID()] = {};
                    }
                    errors[node.getID()].okPort = t('editor.verify.errors.okPort');
                }
                if (!node.getImage() && !node.getAudio()) {
                    console.log('Missing asset.');
                    if (!errors[node.getID()]) {
                        errors[node.getID()] = {};
                    }
                    errors[node.getID()].assets = t('editor.verify.errors.assets');
                }
                // OK and HOME transitions cannot link to the same stage node
                if ((node.getControls().ok || node.getControls().autoplay) && node.onOk(model)[0] === node) {
                    console.log('Invalid link on OK port: ' + (node.okPort && node.okPort.getName()));
                    if (!errors[node.getID()]) {
                        errors[node.getID()] = {};
                    }
                    errors[node.getID()].okPort = t('editor.verify.errors.invalidOkPort');
                }
                if (node.getControls().home && node.onHome(model)[0] === node) {
                    console.log('Invalid link on HOME port: ' + (node.homePort && node.homePort.getName()));
                    if (!errors[node.getID()]) {
                        errors[node.getID()] = {};
                    }
                    errors[node.getID()].homePort = t('editor.verify.errors.invalidHomePort');
                }

            } else if (node instanceof ActionNodeModel) {
                let optionsIn = node.optionsIn || [];
                optionsIn = node.randomOptionIn ? optionsIn.concat([node.randomOptionIn]) : optionsIn;
                if (optionsIn.reduce((acc,optIn) => acc + Object.keys(optIn.getLinks()).length, 0) < 1) {
                    console.log('Missing link on OPTION IN ports');
                    if (!errors[node.getID()]) {
                        errors[node.getID()] = {};
                    }
                    errors[node.getID()]['optionsIn'] = t('editor.verify.errors.optionsIn');
                }
                node.optionsOut.forEach((out,idx) => {
                    if (Object.keys(out.getLinks()).length !== 1) {
                        console.log('Missing link on OPTION #'+(idx+1)+' OUT port: ' + out.getName());
                        if (!errors[node.getID()]) {
                            errors[node.getID()] = {};
                        }
                        errors[node.getID()]['optionsOut_'+idx] = t('editor.verify.errors.optionsOut', { index: idx+1 });
                    }
                });
                actionNodesCount++;
            } else if (node instanceof  MenuNodeModel) {
                if (node.fromPort && Object.keys(node.fromPort.getLinks()).length < 1) {
                    console.log('Missing link on FROM port: ' + node.fromPort.getName());
                    if (!errors[node.getID()]) {
                        errors[node.getID()] = {};
                    }
                    errors[node.getID()].fromPort = t('editor.verify.errors.fromPort');
                }
                node.optionsOut.forEach((out,idx) => {
                    if (Object.keys(out.getLinks()).length !== 1) {
                        console.log('Missing link on OPTION #'+(idx+1)+' OUT port: ' + out.getName());
                        if (!errors[node.getID()]) {
                            errors[node.getID()] = {};
                        }
                        errors[node.getID()]['optionsOut_'+idx] = t('editor.verify.errors.optionsOut', { index: idx+1 });
                    }
                    if (!node.getOptionImage(idx) && !node.getOptionAudio(idx)) {
                        console.log('Missing option asset.');
                        if (!errors[node.getID()]) {
                            errors[node.getID()] = {};
                        }
                        errors[node.getID()]['assets_'+idx] = t('editor.verify.errors.optionAssets', { index: idx+1 });
                    }
                });
            }
        });

        // Update highlighted errors
        this.props.setDiagramErrors(errors);

        // Notification
        let errorCount = Object.keys(errors).length;
        if (errorCount > 0) {
            toast.error(t('toasts.editor.verifyDiagramErrors', {count: errorCount}));
        } else {
            if (model.nightModeAvailable && actionNodesCount === 0) {
                toast.error(t('toasts.editor.verifyDiagramNightModeNoActionNodes'));
            } else {
                toast.success(t('toasts.editor.verifyDiagramOK'));
            }
        }
    };

    showHelpDialog = (helpId) => {
        return () => this.setState({showHelpDialog: helpId});
    };

    dismissHelpDialog = () => {
        this.setState({showHelpDialog: null});
    };

    renderHelpDialog = () => {
        const { t } = this.props;
        let key = 'dialogs.editor.help.' + this.state.showHelpDialog;
        return <Modal id={`${this.state.showHelpDialog}-help-dialog`}
               className="help-dialog"
               title={<span dangerouslySetInnerHTML={{__html: t(key+'.title')}}/>}
               content={<div dangerouslySetInnerHTML={{__html: t(key+'.content')}} ></div>}
               buttons={[
                   { label: t('dialogs.shared.ok'), onClick: this.dismissHelpDialog}
               ]}
               onClose={this.dismissHelpDialog}
        />
    };

    render() {
        const { t } = this.props;
        let defaultImage = 'data:image/png;base64,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';
        return (<div className="pack-diagram-widget">

            {this.state.showFilenameSuggestionDialog &&
            <Modal id="suggest-pack-filename"
                   title={t('dialogs.editor.filenameSuggestion.title')}
                   content={<>
                       <p>{t('dialogs.editor.filenameSuggestion.content')}</p>
                       <p>{t('dialogs.editor.filenameSuggestion.line2')}</p>
                    </>}
                   buttons={[
                       { label: t('dialogs.shared.no'), onClick: this.dismissFilenameSuggestionDialog},
                       { label: t('dialogs.shared.yes'), onClick: this.useSuggestedFilename}
                   ]}
                   onClose={this.dismissFilenameSuggestionDialog}
            />}
            {this.state.showHelpDialog && this.renderHelpDialog()}

            {/* Metadata */}
            <div className="metadata">
                <div className="metadata-section vertical">
                    <div>
                        <label htmlFor="pack-title">{t('editor.metadata.title')}</label>
                        <input id="pack-title" type="text" value={this.getDiagramModel().title || ''} onChange={this.changeTitle} onFocus={this.onInputFocus} onBlur={this.checkFilename}/>
                    </div>
                    <div>
                        <label htmlFor="pack-version">{t('editor.metadata.version')}</label>
                        <input id="pack-version" type="number" value={this.getDiagramModel().version || ''} onChange={this.changeVersion} onFocus={this.onInputFocus} onBlur={this.checkFilename}/>
                    </div>
                    <div>
                        <label htmlFor="pack-filename">{t('editor.metadata.filename')}</label>
                        <input id="pack-filename" type="text" value={this.props.editor.filename || ''} onChange={this.changeFilename} onFocus={this.onInputFocus} onBlur={this.onInputBlur}/>
                    </div>
                    <div>
                        <label>{t('editor.metadata.uuid')}</label>
                        <span>{this.props.diagramEngine.getModel() && this.props.diagramEngine.getModel().getEntryPoint() && this.props.diagramEngine.getModel().getEntryPoint().getUuid()}</span>
                    </div>
                </div>
                <div className="metadata-section vertical">
                    <div>
                        <label htmlFor="pack-description">{t('editor.metadata.desc')}</label>
                        <textarea id="pack-description" value={this.getDiagramModel().description || ''} style={{display: 'inline-block'}} onChange={this.changeDescription} onFocus={this.onInputFocus} onBlur={this.onInputBlur}/>
                    </div>
                    <div title={t('editor.metadata.nightModeWarning')}>
                        <label htmlFor="pack-night-mode">{t('editor.metadata.nightMode')}</label>
                        <Switch onChange={this.changeNightModeAvailable} checked={this.getDiagramModel().nightModeAvailable} height={15} width={35} handleDiameter={20} boxShadow="0px 1px 5px rgba(0, 0, 0, 0.6)" activeBoxShadow="0px 0px 1px 10px rgba(0, 0, 0, 0.2)" uncheckedIcon={false} checkedIcon={false} className="metadata-night-mode" />
                    </div>
                </div>
                <div className="metadata-section right">
                    <label htmlFor="pack-thumb">{t('editor.metadata.thumb')}</label>
                    <input type="file" id="pack-thumb" style={{visibility: 'hidden', position: 'absolute'}} onChange={this.changeThumbnail} />
                    <img src={this.getDiagramModel().thumbnail || defaultImage} alt="" width="120" height="120" onClick={this.showThumbnailSelector} />
                </div>
            </div>

            <div className="content">
                {/* Node tray */}
                <TrayWidget>
                    <TrayItemWidget model={{ type: "cover" }} className="tray-item-cover" helpClicked={this.showHelpDialog('cover')}>
                        <span className="glyphicon glyphicon-book"/> {t('editor.tray.cover')}
                    </TrayItemWidget>
                    <TrayItemWidget model={{ type: "menu" }} className="tray-item-menu" helpClicked={this.showHelpDialog('menu')}>
                        <span className="glyphicon glyphicon-question-sign"/> {t('editor.tray.menu')}
                    </TrayItemWidget>
                    <TrayItemWidget model={{ type: "story" }} className="tray-item-story" helpClicked={this.showHelpDialog('story')}>
                        <span className="glyphicon glyphicon-headphones"/> {t('editor.tray.story')}
                    </TrayItemWidget>
                    <hr />
                    <TrayItemWidget model={{ type: "stage" }} className="tray-item-stage" helpClicked={this.showHelpDialog('stage')}>
                        {t('editor.tray.stage')}
                    </TrayItemWidget>
                    <TrayItemWidget model={{ type: "action" }} className="tray-item-action" helpClicked={this.showHelpDialog('action')}>
                        {t('editor.tray.action')}
                    </TrayItemWidget>
                    <button onClick={this.showHelpDialog('diagram')} title={t('editor.tray.help')} className="help glyphicon glyphicon-info-sign"/>
                </TrayWidget>

                {/* Diagram */}
                {this.props.diagramEngine.getModel() && <div className="diagram-drop-zone"
                     onDrop={this.onDropNode}
                     onDragOver={event => { event.preventDefault(); }}>
                    <CanvasWidget className={`storm-diagrams-canvas ${this.state.displayForwardWorkflow ? '' : 'hide-forward-workflow'} ${this.state.displayReturningWorkflow ? '' : 'hide-returning-workflow'}`} engine={this.props.diagramEngine}/>
                    {/* Tools */}
                    <div className="toolbelt">
                        <div className="tool"><span title={t('editor.tools.zoom')} className="btn btn-default glyphicon glyphicon-fullscreen" onClick={this.zoomToFit}/></div>
                        <div className="tool"><span title={t('editor.tools.clone')} className="btn btn-default glyphicon glyphicon-duplicate" onClick={this.cloneSelection}/></div>
                        <div className="tool layers">
                            <span title={t('editor.tools.filterLayers')} className="btn btn-default glyphicon glyphicon-filter"/>
                            <ul>
                                <li>
                                    <label htmlFor="display-forward-workflow-checkbox">{t('editor.tools.displayForwardWorkflow')}</label>
                                    <input type="checkbox" id="display-forward-workflow-checkbox" checked={this.state.displayForwardWorkflow} onChange={this.toggleDisplayForwardWorkflow} />
                                </li>
                                <li>
                                    <label htmlFor="display-returning-workflow-checkbox">{t('editor.tools.displayReturningWorkflow')}</label>
                                    <input type="checkbox" id="display-returning-workflow-checkbox" checked={this.state.displayReturningWorkflow} onChange={this.toggleDisplayReturningWorkflow} />
                                </li>
                            </ul>
                        </div>
                        <div className="tool"><span title={t('editor.tools.verify')} className="btn btn-default glyphicon glyphicon-ok-sign" onClick={this.verifyDiagram}/></div>
                    </div>
                </div>}
            </div>

        </div>);
    }

}

PackDiagramWidget.propTypes = {
    diagramEngine: PropTypes.instanceOf(DiagramEngine).isRequired
};

const mapStateToProps = (state, ownProps) => ({
    editor: state.editor
});

const mapDispatchToProps = (dispatch, ownProps) => ({
    setEditorFilename: (filename) => dispatch(setEditorFilename(filename)),
    setEditorDiagram: (diagram, filename) => dispatch(setEditorDiagram(diagram, filename)),
    setDiagramErrors: (errors) => dispatch(setDiagramErrors(errors))
});

export default withTranslation()(
    connect(
        mapStateToProps,
        mapDispatchToProps
    )(PackDiagramWidget)
)
