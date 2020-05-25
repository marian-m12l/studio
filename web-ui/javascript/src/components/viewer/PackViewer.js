/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import React from 'react';
import PropTypes from "prop-types";
import {withTranslation} from "react-i18next";

import './PackViewer.css';


class PackViewer extends React.Component {

    constructor(props) {
        super(props);
        this.audioRef = React.createRef();
    }

    wheelClickedLeft = (e) => {
        e.preventDefault();
        if (this.props.controls.wheel && this.props.wheelClickedLeft) {
            this.props.wheelClickedLeft(e);
        }
    };

    wheelClickedRight = (e) => {
        e.preventDefault();
        if (this.props.controls.wheel && this.props.wheelClickedRight) {
            this.props.wheelClickedRight(e);
        }
    };

    homeClicked = (e) => {
        e.preventDefault();
        if (this.props.controls.home && this.props.homeClicked) {
            this.props.homeClicked(e);
        }
    };

    pauseClicked = (e) => {
        e.preventDefault();
        if (this.props.controls.pause) {
            // Pause audio playback
            if (this.audioRef && this.audioRef.current) {
                this.audioRef.current.paused ? this.audioRef.current.play() : this.audioRef.current.pause();
            }
            if (this.props.pauseClicked) {
                this.props.pauseClicked(e);
            }
        }
    };

    okClicked = (e) => {
        e.preventDefault();
        // OK button is used to replace autoplay when autoplay is disabled
        if (
            (this.props.controls.ok
                || (
                    !this.props.options.autoplay
                    && this.props.controls.autoplay
                    && (
                        !this.audioRef
                        || this.audioRef.current.ended
                    )
                )
            ) && this.props.okClicked) {
            this.props.okClicked(e);
        }
    };

    audioEnded = (e) => {
        // Autoplay may be disabled
        if (this.props.controls.autoplay && this.props.options.autoplay && this.props.okClicked) {
            this.props.okClicked(e);
        }
    };

    close = (e) => {
        e.preventDefault();
        // Stop audio
        if (this.audioRef && this.audioRef.current) {
            this.audioRef.current.pause();
        }
        if (this.props.closeClicked) {
            this.props.closeClicked(e);
        }
    };

    toggleOption = (optionName) => {
        return (e) => {
            e.preventDefault();
            if (this.props.optionToggled) {
                this.props.optionToggled(optionName, e);
            }
        };
    };

    render() {
        const { t } = this.props;
        return (
            <div className="pack-viewer">
                <svg width="1022" height="557">
                    {/* Blur filter when casing overlay is ON */}
                    <defs>
                        <filter id="blurryOverlay">
                            <feGaussianBlur in="SourceGraphic" stdDeviation=".8" />
                        </filter>
                    </defs>

                    {/* Story teller case */}
                    <path className="casing"
                          d="M46,2 h930 a44,44 0 0 1 44,44 v465 a44,44 0 0 1 -44,44 h-930 a44,44 0 0 1 -44,-44 v-465 a44,44 0 0 1 44,-44 z" />

                    {/* Screen with asset displayed */}
                    <image id="asset" x="415" y="153" width="340" height="255" filter={this.props.options.overlay ? 'url(#blurryOverlay)' : ''}
                           xlinkHref={this.props.image}/>

                    {/* (Optional) Translucent casing in front of screen */}
                    {this.props.options.overlay &&
                    <path className="casing translucent"
                          d="M46,2 h930 a44,44 0 0 1 44,44 v465 a44,44 0 0 1 -44,44 h-930 a44,44 0 0 1 -44,-44 v-465 a44,44 0 0 1 44,-44 z"/>
                    }

                    {/* Option buttons */}
                    {/* eslint-disable-next-line */}
                    <a href="#" className="close" onClick={this.toggleOption('translucent')}>
                        <title>{t('viewer.options.translucent')}</title>
                        <text x="50" y="40" fontSize="32">{this.props.options.translucent ? '☼' : '☀'}</text>
                    </a>
                    {/* eslint-disable-next-line */}
                    <a href="#" className="close" onClick={this.toggleOption('overlay')}>
                        <title>{t('viewer.options.overlay')}</title>
                        <text x="90" y="40" fontSize="32">{this.props.options.overlay ? '▩' : '□'}</text>
                    </a>
                    {/* eslint-disable-next-line */}
                    <a href="#" className="close" onClick={this.toggleOption('autoplay')}>
                        <title>{t('viewer.options.autoplay')}</title>
                        <text x="130" y="40" fontSize="32">{this.props.options.autoplay ? '▶' : '▷'}</text>
                    </a>

                    {/* Audio controls */}
                    <foreignObject x="415" y="90" width="340" height="55">
                        {this.props.audio && <audio ref={this.audioRef} src={this.props.audio} autoPlay controls preload="metadata" onEnded={this.audioEnded} />}
                    </foreignObject>

                    {/* Close button */}
                    {/* eslint-disable-next-line */}
                    <a href="#" className="close" onClick={this.close}>
                        <title>{t('viewer.close')}</title>
                        <text x="950" y="60" fontSize="64">&times;</text>
                    </a>

                    {/* Wheel */}
                    <circle cx="242" cy="280" r="130" strokeWidth="4" stroke="#eead45" fill="#febd55"/>
                    {/* eslint-disable-next-line */}
                    <a href="#" onClick={this.wheelClickedLeft}>
                        <path className="actionable" d="M177,167 A130,130 0 0 0 177,393" strokeWidth="4"
                              stroke="transparent" fill="transparent"/>
                    </a>
                    {/* eslint-disable-next-line */}
                    <a href="#" onClick={this.wheelClickedRight}>
                        <path className="actionable" d="M307,167 A130,130 0 0 1 307,393" strokeWidth="4"
                              stroke="transparent" fill="transparent"/>
                    </a>

                    {/* Home */}
                    {/* eslint-disable-next-line */}
                    <a href="#" onClick={this.homeClicked}>
                        <g className="button actionable">
                            <circle className="button actionable" cx="850" cy="117" r="30" />
                            <text x="832" y="130" fontSize="48" fontWeight="bold">&#8962;</text>
                        </g>
                    </a>

                    {/* Pause */}
                    {/* eslint-disable-next-line */}
                    <a href="" onClick={this.pauseClicked}>
                        <g className="button actionable">
                            <circle className="button actionable" cx="850" cy="242" r="30" />
                            <text x="832" y="250" fontSize="32">&#9646;&#9646;</text>
                        </g>
                    </a>

                    {/* OK */}
                    {/* eslint-disable-next-line */}
                    <a href="" onClick={this.okClicked}>
                        <g className="button actionable">
                            <circle cx="850" cy="407" r="55"/>
                            <text x="808" y="425" fontSize="48" fontWeight="bold">OK</text>
                        </g>
                    </a>

                </svg>
            </div>
        );
    }
}

PackViewer.propTypes = {
    image: PropTypes.string,    // data url
    audio: PropTypes.string,    // data url
    wheelClickedLeft: PropTypes.func,
    wheelClickedRight: PropTypes.func,
    homeClicked: PropTypes.func,
    pauseClicked: PropTypes.func,
    okClicked: PropTypes.func,
    closeClicked: PropTypes.func,
    controls: PropTypes.shape({
        wheel: PropTypes.bool.isRequired,
        ok: PropTypes.bool.isRequired,
        home: PropTypes.bool.isRequired,
        pause: PropTypes.bool.isRequired,
        autoplay: PropTypes.bool.isRequired
    }),
    options: PropTypes.shape({
        translucent: PropTypes.bool.isRequired,
    }),
    optionToggled: PropTypes.func
};

export default withTranslation()(PackViewer);
