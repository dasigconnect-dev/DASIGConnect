import { useState } from "react";
import type { SavedMediaAsset } from "../../../api/submissionApi";
import OptimizedImage, { canTransformImageType } from "../../../components/media/OptimizedImage";
import { isImageFileType, isVideoFileType } from "../utils";

export function SubmissionCardMedia({
  thumbnail,
  mediaCount = 0,
  detailsLoaded = false,
}: {
  thumbnail?: SavedMediaAsset;
  mediaCount?: number;
  detailsLoaded?: boolean;
}) {
  const [loaded, setLoaded] = useState(false);
  const [hasError, setHasError] = useState(false);

  // If there is media attached (mediaCount > 0) but the thumbnail is still being fetched from backend:
  if (mediaCount > 0 && !thumbnail?.storageUrl && !detailsLoaded) {
    return (
      <div className="sub-fb-media-container">
        <div className="sub-fb-media-loader" aria-label="Loading media">
          <div className="sub-fb-spinner"></div>
        </div>
      </div>
    );
  }

  // If there is no media attached or media failed to load:
  if (!thumbnail?.storageUrl || hasError || mediaCount === 0) {
    return (
      <div className="sub-fb-media-container">
        <div className="sub-fb-no-media">
          <i className="ti ti-photo"></i>
          <span>No media attached</span>
        </div>
      </div>
    );
  }

  const isImg = isImageFileType(thumbnail.fileType);
  const isVid = isVideoFileType(thumbnail.fileType);

  return (
    <div className="sub-fb-media-container">
      {!loaded && (
        <div className="sub-fb-media-loader" aria-label="Loading media">
          <div className="sub-fb-spinner"></div>
        </div>
      )}
      {isImg ? (
        <OptimizedImage
          src={thumbnail.storageUrl}
          alt=""
          className={`sub-fb-media-img${loaded ? " is-loaded" : " is-loading"}`}
          width={640}
          height={360}
          sizes="(max-width: 768px) 100vw, 360px"
          candidateWidths={[360, 640, 960]}
          transform={canTransformImageType(thumbnail.fileType)}
          onLoad={() => setLoaded(true)}
          onError={() => {
            setLoaded(true);
            setHasError(true);
          }}
        />
      ) : isVid ? (
        <video
          src={thumbnail.storageUrl}
          muted
          playsInline
          preload="metadata"
          className={`sub-fb-media-video${loaded ? " is-loaded" : " is-loading"}`}
          onLoadedData={() => setLoaded(true)}
          onError={() => {
            setLoaded(true);
            setHasError(true);
          }}
        />
      ) : null}
      {(mediaCount ?? 0) > 1 && loaded && (
        <span className="sub-fb-media-badge">
          <i className="ti ti-photo-copy"></i>
          +{(mediaCount ?? 0) - 1} photos
        </span>
      )}
    </div>
  );
}
