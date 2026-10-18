-- Seed sample published posts with engagement metrics for DASIGConnect
-- This populates data for AI "Generate from Top Posts" template generator (UC-1.5)
-- and analytics / engagement views.

DO $$
DECLARE
    v_user_id uuid;
    v_inst_id uuid;
    v_sub_id uuid;
BEGIN
    SELECT id INTO v_user_id FROM users WHERE email = 'markantoncamoro@gmail.com' LIMIT 1;
    IF v_user_id IS NULL THEN
        SELECT id INTO v_user_id FROM users WHERE role = 'admin' LIMIT 1;
    END IF;
    IF v_user_id IS NULL THEN
        SELECT id INTO v_user_id FROM users LIMIT 1;
    END IF;

    SELECT id INTO v_inst_id FROM institutions WHERE name = 'DASIG Central Visayas' LIMIT 1;
    IF v_inst_id IS NULL THEN
        SELECT id INTO v_inst_id FROM institutions LIMIT 1;
    END IF;

    -- Clean up any prior sample posts with this tag/prefix if re-run
    DELETE FROM submissions WHERE platform_post_id LIKE 'sample_top_post_%';

    -- 1. Regional Innovation Summit 2026 (Score ~305)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'Regional Innovation Summit 2026', CURRENT_DATE - 15,
        '🚀 INNOVATION IN ACTION: Central Visayas Regional Innovation Summit 2026 is officially here!' || E'\n\n' ||
        'Join over 500+ researchers, technopreneurs, and academic leaders across Region 7 as we showcase cutting-edge R&D breakthroughs, tech commercialization pitches, and university-industry linkages.' || E'\n\n' ||
        '📅 Date: October 24-25, 2026' || E'\n' ||
        '📍 Venue: Sky Hall Convention Center, Cebu City' || E'\n' ||
        '🔗 Register now (Free admission): https://dasigconnect.gov.ph/summit2026' || E'\n\n' ||
        'Be part of shaping our region’s innovation frontier! Limited slots available.' || E'\n\n' ||
        '#DASIGCentralVisayas #DOST7 #RegionalInnovation #ScienceForThePeople #OneDOST4U',
        'Official announcement for the annual Regional Innovation Summit', 'published', NOW() - INTERVAL '15 days', 'sample_top_post_1',
        'Events', 'Innovation,DOST,Summit', NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 4520, 142, 38, 29, NOW(), NOW() - INTERVAL '15 days', NOW()
    );

    -- 2. AI & Robotics Pitching Competition Call (Score ~313)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'AI & Robotics Innovation Challenge 2026', CURRENT_DATE - 22,
        '💡 CALLING ALL STUDENT INNOVATORS & TECH TEAMS! 🤖' || E'\n\n' ||
        'Do you have a prototype ready to make a regional impact? The DASIG R&D Innovation Challenge is now open for applications!' || E'\n\n' ||
        'Top 3 winning teams receive:' || E'\n' ||
        '🏆 Up to ₱150,000 in prototyping grant funding' || E'\n' ||
        '🤝 Mentorship from DOST-certified startup incubators' || E'\n' ||
        '🌐 Direct exhibition slot at National Science & Technology Week' || E'\n\n' ||
        '⏰ Deadline for pitch decks: November 15, 2026' || E'\n' ||
        '👉 Apply your team today: https://dasigconnect.gov.ph/pitch2026' || E'\n\n' ||
        'Tag your teammates below and let’s turn research into real-world solutions!' || E'\n\n' ||
        '#DASIGCentralVisayas #DOST7 #InnovationChallenge #StudentInnovators #YouthInTech',
        'Call for proposals for regional student technology hackathon', 'published', NOW() - INTERVAL '22 days', 'sample_top_post_2',
        'Competitions', 'Robotics,AI,DOST7', NOW() - INTERVAL '22 days', NOW() - INTERVAL '22 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 5100, 118, 45, 35, NOW(), NOW() - INTERVAL '22 days', NOW()
    );

    -- 3. Recap: Central Visayas Health Research Conference (Score ~217)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'Recap: Central Visayas Health Research Conference', CURRENT_DATE - 30,
        '🌟 CELEBRATING VISAYAS RESEARCH EXCELLENCE! 🩺🇵🇭' || E'\n\n' ||
        'Over 350 researchers and medical scientists gathered for the 2026 Central Visayas Health Research Conference! Highlights include:' || E'\n' ||
        '✨ 42 peer-reviewed health innovation poster presentations' || E'\n' ||
        '✨ 8 commercialization MOAs signed between HEIs and local industry partners' || E'\n' ||
        '✨ Keynote address by DOST Region 7 Director on AI diagnostics for rural clinics' || E'\n\n' ||
        'Congratulations to all our oral presentation awardees from CIT-U, USC, and CNU!' || E'\n\n' ||
        'Full photo album and presentation materials available at: https://dasigconnect.gov.ph/recap-health2026' || E'\n\n' ||
        '#DASIGCentralVisayas #DOST7 #HealthResearch #VisayasInnovation #ResearchExcellence',
        'Recap and achievement highlights from health research conference', 'published', NOW() - INTERVAL '30 days', 'sample_top_post_3',
        'Milestones', 'Health,Research,DOST7', NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 3800, 95, 28, 22, NOW(), NOW() - INTERVAL '30 days', NOW()
    );

    -- 4. Webinar: Patent Protection & Tech Transfer for HEIs (Score ~174)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'Patent Protection & Tech Transfer Masterclass', CURRENT_DATE - 38,
        '⚖️ TURN YOUR THESIS INTO A PROTECTED PATENT! 📜' || E'\n\n' ||
        'Navigating intellectual property rights shouldn''t be intimidating. Join our hands-on virtual masterclass with IPOPHL specialists.' || E'\n\n' ||
        'Topics covered:' || E'\n' ||
        '🔹 Prior art search techniques for engineering & biotech' || E'\n' ||
        '🔹 Drafting patent claims that hold up commercially' || E'\n' ||
        '🔹 Fast-track filing subsidies under the DOST Support Fund' || E'\n\n' ||
        '🗓 Date: November 5, 2026 | 2:00 PM – 4:30 PM (via Zoom)' || E'\n' ||
        '🎟 Register here: https://dasigconnect.gov.ph/ip-webinar' || E'\n\n' ||
        'Certificate of Attendance provided to all registered participants!' || E'\n\n' ||
        '#DASIGCentralVisayas #DOST7 #IntellectualProperty #TechTransfer #AcademeToIndustry',
        'Capacity building seminar on patent drafting and protection', 'published', NOW() - INTERVAL '38 days', 'sample_top_post_4',
        'Workshops', 'IP,Patents,DOST7', NOW() - INTERVAL '38 days', NOW() - INTERVAL '38 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 2900, 82, 19, 18, NOW(), NOW() - INTERVAL '38 days', NOW()
    );

    -- 5. DOST Grants-in-Aid (GIA) Call for Proposals 2027 (Score ~169)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'DOST Grants-in-Aid (GIA) Call for Proposals 2027', CURRENT_DATE - 45,
        '📢 ATTENTION FACULTY RESEARCHERS: DOST Region 7 Grants-in-Aid (GIA) Cycle is now OPEN!' || E'\n\n' ||
        'Priority research tracks:' || E'\n' ||
        '🌱 Agri-tech & Food Security Resilience' || E'\n' ||
        '🌊 Marine Biodiversity & Coastal Protection' || E'\n' ||
        '🏭 Smart Manufacturing & Industry 4.0 Solutions' || E'\n\n' ||
        'Submission Portal closes on December 1, 2026.' || E'\n' ||
        'Guidelines and proposal templates: https://dasigconnect.gov.ph/gia-proposals' || E'\n\n' ||
        'Consult with your university R&D coordinator to initiate institutional endorsement.' || E'\n\n' ||
        '#DASIGCentralVisayas #DOST7 #ResearchGrants #GIA2027 #PhilippineScience',
        'Announcement of annual regional research grant funding opportunities', 'published', NOW() - INTERVAL '45 days', 'sample_top_post_5',
        'Grants', 'Research,Grants,DOST7', NOW() - INTERVAL '45 days', NOW() - INTERVAL '45 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 3200, 76, 24, 15, NOW(), NOW() - INTERVAL '45 days', NOW()
    );

    -- 6. Tech Showcase: Sustainable Agri-Waste Bioplastics (Score ~134)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'Sustainable Agri-Waste Bioplastics Innovation Spotlight', CURRENT_DATE - 52,
        '🌱 FROM CASSAVA PEEL TO COMMERCIAL PACKAGING: Meet the researcher team behind Biopack Visayas!' || E'\n\n' ||
        'Supported by the DASIG HEI Consortium, researchers from Cebu have developed 100% biodegradable packaging material that decomposes within 45 days.' || E'\n\n' ||
        '🔬 85% tensile strength compared to conventional plastics' || E'\n' ||
        '🌊 Safe for marine life with zero microplastic residue' || E'\n' ||
        '📈 Currently undergoing pilot scale testing in Mandaue City' || E'\n\n' ||
        'Read the full research spotlight: https://dasigconnect.gov.ph/biopack-spotlight' || E'\n\n' ||
        '#DASIGCentralVisayas #DOST7 #SustainableTech #CircularEconomy #GreenInnovation',
        'Technology highlight featuring regional circular economy breakthrough', 'published', NOW() - INTERVAL '52 days', 'sample_top_post_6',
        'Technology', 'Biotech,Sustainability,DOST7', NOW() - INTERVAL '52 days', NOW() - INTERVAL '52 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 2400, 68, 15, 12, NOW(), NOW() - INTERVAL '52 days', NOW()
    );

    -- 7. Workshop: Data Science & AI for Smart Cities (Score ~103)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'Data Science Bootcamp for Region 7 Faculty', CURRENT_DATE - 60,
        '📊 LEVEL UP YOUR SKILLS: 3-Day Intensive Data Science Bootcamp for Central Visayas Faculty!' || E'\n\n' ||
        'Master Python, exploratory data analysis, and predictive models applied to urban planning and flood risk monitoring.' || E'\n\n' ||
        '📅 Dates: November 18-20, 2026' || E'\n' ||
        '📍 CIT-U Innovation Lab, Cebu City' || E'\n' ||
        '🔗 Slots are limited to 30 faculty participants: https://dasigconnect.gov.ph/datascience-bootcamp' || E'\n\n' ||
        '#DASIGCentralVisayas #DOST7 #DataScience #SmartCities #CapacityBuilding',
        'Faculty training workshop on computational data science', 'published', NOW() - INTERVAL '60 days', 'sample_top_post_7',
        'Workshops', 'DataScience,Training,DOST7', NOW() - INTERVAL '60 days', NOW() - INTERVAL '60 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 1950, 54, 11, 9, NOW(), NOW() - INTERVAL '60 days', NOW()
    );

    -- 8. Regional FabLab Network Prototyping Exchange (Score ~78)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'Regional FabLab Network Prototyping Exchange', CURRENT_DATE - 70,
        '🛠 FABRICATING THE FUTURE: FabLab Bohol & FabLab UP Cebu join forces!' || E'\n\n' ||
        'Makers, engineers, and digital fabrication specialists came together for a cross-facility prototyping exchange. Over 15 assistive device designs were finalized for local PWD communities.' || E'\n\n' ||
        'Check out open-source blueprints: https://dasigconnect.gov.ph/fablab-exchange' || E'\n\n' ||
        '#DASIGCentralVisayas #DOST7 #FabLab #DigitalFabrication #CommunityTech',
        'Prototyping collaborative work across member institution FabLabs', 'published', NOW() - INTERVAL '70 days', 'sample_top_post_8',
        'Innovation', 'FabLab,Engineering,DOST7', NOW() - INTERVAL '70 days', NOW() - INTERVAL '70 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 1500, 41, 8, 7, NOW(), NOW() - INTERVAL '70 days', NOW()
    );

    -- 9. Contrast post: Low engagement (Score ~4)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'Office Notice: Holiday Schedule', CURRENT_DATE - 80,
        'Notice: The DASIG regional coordination office will be closed on November 2 in observance of All Souls'' Day. Regular operations resume November 3.',
        'Holiday schedule announcement', 'published', NOW() - INTERVAL '80 days', 'sample_top_post_9',
        'Announcements', 'Office,Notice', NOW() - INTERVAL '80 days', NOW() - INTERVAL '80 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 350, 4, 0, 0, NOW(), NOW() - INTERVAL '80 days', NOW()
    );

    -- 10. Contrast post: Low engagement (Score ~4)
    v_sub_id := gen_random_uuid();
    INSERT INTO submissions (
        id, contributor_id, institution_id, event_title, event_date,
        caption, description, status, published_at, platform_post_id,
        category, tags, created_at, updated_at
    ) VALUES (
        v_sub_id, v_user_id, v_inst_id, 'Brief Reminder: Monthly Progress Reports', CURRENT_DATE - 90,
        'Friendly reminder: Monthly progress reports for Q3 are due this Friday.',
        'Routine administrative reminder', 'published', NOW() - INTERVAL '90 days', 'sample_top_post_10',
        'Announcements', 'Reports,Deadline', NOW() - INTERVAL '90 days', NOW() - INTERVAL '90 days'
    );
    INSERT INTO submission_engagement_metrics (
        id, submission_id, reach, reactions, comments_count, shares, fetched_at, created_at, updated_at
    ) VALUES (
        gen_random_uuid(), v_sub_id, 280, 2, 1, 0, NOW(), NOW() - INTERVAL '90 days', NOW()
    );

END $$;
