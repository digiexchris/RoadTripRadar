import React from 'react';
import './RadarTimeline.css';

interface RadarFrame {
    url: string;
    timestamp: number;
}

interface RadarTimelineProps {
    frames: RadarFrame[];
    currentFrameIndex: number;
    radarMode: 'history' | 'now';
    onToggleMode: () => void;
}

export const RadarTimeline: React.FC<RadarTimelineProps> = ({ frames, currentFrameIndex, radarMode, onToggleMode }) => {
    if (frames.length === 0) return null;

    const formatTime = (timestamp: number): string => {
        const date = new Date(timestamp * 1000);
        return date.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit',
        });
    };

    return (
        <div className={`radar-timeline ${radarMode === 'now' ? 'now-mode' : ''}`} onClick={onToggleMode}>
            {radarMode === 'history' ? (
                <div className="timeline-bar">
                    {frames.map((frame, index) => (
                        <div
                            key={frame.timestamp}
                            className={`timeline-segment ${index === currentFrameIndex ? 'active' : ''} ${index < currentFrameIndex ? 'past' : ''
                                }`}
                            style={{ width: `${100 / frames.length}%` }}
                        >
                            <div className="timeline-marker"></div>
                            <div className="timeline-label">{formatTime(frame.timestamp)}</div>
                        </div>
                    ))}
                </div>
            ) : (
                <div className="timeline-bar now-bar">
                    <div className="now-label">As of {formatTime(frames[frames.length - 1].timestamp)}</div>
                </div>
            )}
        </div>
    );
};
