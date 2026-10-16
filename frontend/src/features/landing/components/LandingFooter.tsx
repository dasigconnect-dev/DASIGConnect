interface LandingFooterProps {
  onOpenPrivacy?: () => void;
}

export default function LandingFooter({ onOpenPrivacy }: LandingFooterProps) {
  return (
    <footer className="landing-footer">
      <div className="landing-container">
        <div className="footer-bottom">
          <div>
            &copy; {new Date().getFullYear()} DASIGConnect &bull; DOST Region 7 &times; CIT-U Capstone (Team 2526-sem2-it332-38).
          </div>
          <div className="footer-bottom-info">
            <span>Official Content Coordination System for the DASIG Facebook Page.</span>
            {onOpenPrivacy && (
              <button
                type="button"
                className="footer-privacy-btn"
                onClick={onOpenPrivacy}
              >
                Privacy Policy
              </button>
            )}
          </div>
        </div>
      </div>
    </footer>
  );
}
