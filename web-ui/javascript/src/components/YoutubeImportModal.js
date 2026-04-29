/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import { withTranslation } from 'react-i18next';
import uuidv4 from 'uuid/v4';
import Modal from './Modal';
import { AppContext } from '../AppContext';
import './YoutubeImportModal.css';

class YoutubeImportModal extends React.Component {
    static contextType = AppContext;

    constructor(props) {
        super(props);
        this.state = {
            urlInput: '',
            loading: false,
            error: null,
            data: null,
            title: '',
            batchItems: [], // { url, progress, status, data, title, error, requestId }
            isBatchMode: false
        };
    }

    handleUrlChange = (e) => {
        this.setState({ urlInput: e.target.value, error: null });
    };

    handleTitleChange = (e) => {
        this.setState({ title: e.target.value });
    };

    handleImport = (url) => {
        const { urlInput } = this.state;
        // If called from onClick directly, url will be the event object
        const finalUrl = (typeof url === 'string') ? url : urlInput;
        
        if (!finalUrl || !finalUrl.trim()) {
            this.setState({ error: 'Please enter at least one YouTube URL' });
            return;
        }

        const urls = finalUrl.split('\n').map(u => u.trim()).filter(u => u);
        
        if (urls.length > 1) {
            this.setState({ 
                isBatchMode: true, 
                loading: true, 
                batchItems: urls.map(url => ({ 
                    url, 
                    progress: 0, 
                    status: 'pending',
                    requestId: uuidv4()
                })) 
            }, this.processBatch);
        } else if (urls.length === 1) {
            this.setState({ isBatchMode: false });
            this.importSingle(urls[0]);
        }
    };

    importSingle = async (url) => {
        const { t } = this.props;
        const requestId = uuidv4();
        
        this.setState({ loading: true, error: null, progress: 0, requestId });

        const cleanup = this.setupProgressHandler(requestId, (progress) => {
            this.setState({ progress });
        });

        try {
            const result = await this.performImport(url, requestId);
            if (result.success) {
                this.setState({
                    loading: false,
                    data: result,
                    title: result.title,
                    error: null
                });
            } else {
                this.setState({
                    loading: false,
                    error: result.error || t('youtube.modal.error.importFailed'),
                    data: null
                });
            }
        } catch (err) {
            this.setState({
                loading: false,
                error: t('youtube.modal.error.networkError'),
                data: null
            });
        } finally {
            cleanup();
        }
    };

    processBatch = async () => {
        const { batchItems, processing } = this.state;
        const { onConfirm, autoSave, onClose } = this.props;

        if (processing) return;
        
        this.setState({ processing: true });

        for (let i = 0; i < batchItems.length; i++) {
            const item = batchItems[i];
            if (item.status === 'pending' || item.status === 'error') {
                // Update status to importing
                this.updateBatchItem(i, { status: 'importing' });

                const cleanup = this.setupProgressHandler(item.requestId, (progress, statusMessage) => {
                    this.updateBatchItem(i, { progress, statusMessage });
                });

                try {
                    const result = await this.performImport(item.url, item.requestId);
                    if (result.success) {
                        this.updateBatchItem(i, { 
                            status: 'success', 
                            title: result.title,
                            progress: 1,
                            data: result
                        });
                        
                        // Auto-save if requested
                        if (autoSave && onConfirm) {
                            try {
                                onConfirm({
                                    title: result.title,
                                    thumbnail: result.thumbnail,
                                    audioPath: result.audioPath,
                                    thumbnailPath: result.thumbnailPath
                                }, true); // pass true for auto-confirm/auto-save
                            } catch (e) {
                                console.error("Error in auto-save:", e);
                            }
                        }
                    } else {
                        this.updateBatchItem(i, { status: 'error', error: result.error });
                    }
                } catch (err) {
                    this.updateBatchItem(i, { status: 'error', error: 'Network error' });
                } finally {
                    cleanup();
                }
            }
        }
        
        this.setState({ processing: false });
        
        // If all items are successful, close the modal after a short delay
        const allSuccess = this.state.batchItems.every(item => item.status === 'success');
        if (allSuccess) {
            setTimeout(() => {
                if (onClose) onClose();
            }, 1500);
        }
    };

    setupProgressHandler = (requestId, callback) => {
        const { eventBus } = this.context;
        if (!eventBus) return () => {};

        const handler = (error, message) => {
            if (message && message.body && message.body.requestId === requestId) {
                callback(message.body.progress, message.body.message);
            }
        };
        eventBus.registerHandler(`youtube.progress.${requestId}`, handler);
        return () => eventBus.unregisterHandler(`youtube.progress.${requestId}`, handler);
    };

    performImport = async (url, requestId) => {
        const { eventBus } = this.context;
        
        return new Promise(async (resolve, reject) => {
            let resultReceived = false;
            
            // 1. Setup result handler
            const resultHandler = (error, message) => {
                if (message && message.body) {
                    resultReceived = true;
                    eventBus.unregisterHandler(`youtube.result.${requestId}`, resultHandler);
                    resolve(message.body);
                }
            };
            if (eventBus) {
                eventBus.registerHandler(`youtube.result.${requestId}`, resultHandler);
            }

            try {
                // 2. Trigger import
                const formData = new FormData();
                if (url) formData.append('url', url);
                formData.append('requestId', requestId);

                const response = await fetch('/api/youtube/import', {
                    method: 'POST',
                    body: formData,
                });
                
                const initialResult = await response.json();
                
                if (!initialResult.success) {
                    if (eventBus) {
                        eventBus.unregisterHandler(`youtube.result.${requestId}`, resultHandler);
                    }
                    resolve(initialResult);
                    return;
                }
                
                // If success: true, we continue waiting for the resultHandler to resolve the promise
                // We add a safety timeout (10 minutes)
                setTimeout(() => {
                    if (!resultReceived) {
                        if (eventBus) {
                            eventBus.unregisterHandler(`youtube.result.${requestId}`, resultHandler);
                        }
                        resolve({ success: false, error: 'Import timed out' });
                    }
                }, 600000);

            } catch (err) {
                if (eventBus) {
                    eventBus.unregisterHandler(`youtube.result.${requestId}`, resultHandler);
                }
                reject(err);
            }
        });
    };

    updateBatchItem = (index, updates) => {
        this.setState(prevState => {
            const newBatchItems = [...prevState.batchItems];
            newBatchItems[index] = { ...newBatchItems[index], ...updates };
            return { batchItems: newBatchItems };
        });
    };

    handleConfirm = () => {
        const { onConfirm, onClose } = this.props;
        const { data, title, isBatchMode, batchItems } = this.state;

        if (isBatchMode) {
            batchItems.forEach(item => {
                if (item.status === 'success' && onConfirm) {
                    onConfirm({
                        title: item.title,
                        thumbnail: item.data.thumbnail,
                        audioPath: item.data.audioPath,
                        thumbnailPath: item.data.thumbnailPath
                    });
                }
            });
        } else if (data && onConfirm) {
            onConfirm({
                title,
                thumbnail: data.thumbnail,
                audioPath: data.audioPath,
                thumbnailPath: data.thumbnailPath
            });
        }
        onClose();
    };

    render() {
        const { t, show, onClose } = this.props;
        const { urlInput, loading, error, data, title, progress, isBatchMode, batchItems } = this.state;

        if (!show) return null;

        return (
            <Modal
                id="youtube-import-modal"
                title={t('youtube.modal.title')}
                content={
                    <div className="youtube-import-content">
                        {!data && !isBatchMode ? (
                            <div className="youtube-import-step">
                                <div className="form-group">
                                    <label>{t('youtube.modal.label.url')}</label>
                                    <textarea
                                        className="form-control"
                                        placeholder={t('youtube.modal.placeholder.batchUrls')}
                                        value={urlInput}
                                        onChange={this.handleUrlChange}
                                        disabled={loading}
                                        rows={5}
                                        style={{ resize: 'vertical' }}
                                    />
                                    <small className="text-muted">{t('youtube.modal.help.batch')}</small>
                                </div>
                                {error && <div className="alert alert-danger">{error}</div>}
                                {loading && this.renderProgress(progress)}
                            </div>
                        ) : isBatchMode ? (
                            <div className="youtube-batch-list">
                                {batchItems.map((item, idx) => (
                                    <div key={idx} className={`batch-item status-${item.status}`}>
                                        <div className="batch-item-info">
                                            <span className="batch-item-url">{item.title || item.url}</span>
                                            {item.status === 'error' && <span className="batch-item-error"> - {item.error}</span>}
                                        </div>
                                        <div className="batch-item-progress">
                                            <div className="progress-bar-container">
                                                <div 
                                                    className="progress-bar-fill" 
                                                    style={{ width: `${Math.round(item.progress * 100)}%` }}
                                                />
                                            </div>
                                            <span className="progress-pct">{Math.round(item.progress * 100)}%</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <div className="youtube-import-step">
                                <div className="youtube-preview">
                                    {data.thumbnail && (
                                        <div className="thumbnail-preview">
                                            <img src={`data:image/jpeg;base64,${data.thumbnail}`} alt="" />
                                        </div>
                                    )}
                                    <div className="form-group">
                                        <label>{t('youtube.modal.label.title')}</label>
                                        <input
                                            type="text"
                                            className="form-control"
                                            value={title}
                                            onChange={this.handleTitleChange}
                                        />
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                }
                buttons={
                    !data && !isBatchMode ? [
                        { label: t('dialogs.shared.cancel'), onClick: onClose, disabled: loading },
                        { label: t('youtube.modal.button.import'), onClick: this.handleImport, disabled: loading || !urlInput.trim(), primary: true }
                    ] : isBatchMode ? [
                        { label: t('dialogs.shared.cancel'), onClick: onClose, disabled: loading },
                        { label: t('youtube.modal.button.confirm'), onClick: this.handleConfirm, disabled: loading, primary: true }
                    ] : [
                        { label: t('dialogs.shared.cancel'), onClick: onClose },
                        { label: t('youtube.modal.button.confirm'), onClick: this.handleConfirm, primary: true }
                    ]
                }
                onClose={onClose}
            />
        );
    }

    renderProgress(progress) {
        const { t } = this.props;
        const percentage = Math.round(progress * 100);
        const radius = 45;
        const circumference = 2 * Math.PI * radius;
        const strokeDashoffset = circumference - (progress * circumference);

        return (
            <div className="youtube-loading-container">
                <div className="progress-circle-container">
                    <svg className="progress-circle" width="120" height="120">
                        <circle className="progress-circle-bg" cx="60" cy="60" r={radius} />
                        <circle
                            className="progress-circle-fg"
                            cx="60"
                            cy="60"
                            r={radius}
                            style={{
                                strokeDasharray: circumference,
                                strokeDashoffset: strokeDashoffset
                            }}
                        />
                        <text x="60" y="60" className="progress-text">{percentage}%</text>
                    </svg>
                </div>
                <div className="alert alert-info">{t('youtube.modal.status.downloading')}</div>
            </div>
        );
    }
}

export default withTranslation()(YoutubeImportModal);