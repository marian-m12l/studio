/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import { withTranslation } from 'react-i18next';
import Modal from './Modal';
import './YoutubeImportModal.css';

class YoutubeImportModal extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            url: '',
            loading: false,
            error: null,
            data: null,
            title: ''
        };
    }

    handleUrlChange = (e) => {
        this.setState({ url: e.target.value, error: null });
    };

    handleTitleChange = (e) => {
        this.setState({ title: e.target.value });
    };

    handleImport = async () => {
        const { t } = this.props;
        const { url } = this.state;

        if (!url.trim()) {
            this.setState({ error: t('youtube.modal.error.emptyUrl') });
            return;
        }

        this.setState({ loading: true, error: null });

        try {
            const response = await fetch('/api/youtube/import', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ url }),
            });

            const result = await response.json();

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
            console.error('YouTube import error:', err);
            this.setState({
                loading: false,
                error: t('youtube.modal.error.networkError'),
                data: null
            });
        }
    };

    handleConfirm = () => {
        const { onConfirm, onClose } = this.props;
        const { data, title } = this.state;

        if (data && onConfirm) {
            onConfirm({
                title,
                thumbnail: data.thumbnail,
                audioPath: data.audioPath,
                thumbnailPath: data.thumbnailPath
            });
        }
        onClose();
    };

    handleKeyPress = (e) => {
        if (e.key === 'Enter' && !this.state.loading && !this.state.data) {
            this.handleImport();
        }
    };

    render() {
        const { t, show, onClose } = this.props;
        const { url, loading, error, data, title } = this.state;

        if (!show) {
            return null;
        }

        return (
            <Modal
                id="youtube-import-modal"
                title={t('youtube.modal.title')}
                content={
                    <div className="youtube-import-content">
                        {!data ? (
                            <div className="youtube-import-step">
                                <div className="form-group">
                                    <label>{t('youtube.modal.label.url')}</label>
                                    <input
                                        type="text"
                                        className="form-control"
                                        placeholder="https://www.youtube.com/watch?v=..."
                                        value={url}
                                        onChange={this.handleUrlChange}
                                        onKeyPress={this.handleKeyPress}
                                        disabled={loading}
                                    />
                                </div>
                                {error && (
                                    <div className="alert alert-danger">
                                        {error}
                                    </div>
                                )}
                                {loading && (
                                    <div className="alert alert-info">
                                        {t('youtube.modal.status.downloading')}
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div className="youtube-import-step">
                                <div className="youtube-preview">
                                    {data.thumbnail && (
                                        <div className="thumbnail-preview">
                                            <img
                                                src={`data:image/jpeg;base64,${data.thumbnail}`}
                                                alt={t('youtube.modal.label.thumbnail')}
                                            />
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
                    !data ? [
                        {
                            label: t('dialogs.shared.cancel'),
                            onClick: onClose,
                            disabled: loading
                        },
                        {
                            label: loading ? t('youtube.modal.button.importing') : t('youtube.modal.button.import'),
                            onClick: this.handleImport,
                            disabled: loading || !url.trim(),
                            primary: true
                        }
                    ] : [
                        {
                            label: t('dialogs.shared.cancel'),
                            onClick: onClose
                        },
                        {
                            label: t('youtube.modal.button.confirm'),
                            onClick: this.handleConfirm,
                            primary: true
                        }
                    ]
                }
                onClose={onClose}
            />
        );
    }
}

export default withTranslation()(YoutubeImportModal);