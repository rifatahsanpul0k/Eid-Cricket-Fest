-- Historical finals must remain queryable after a review/rematch while only one
-- completed official final may be authoritative for an edition.
DROP INDEX uq_one_final_per_edition;

CREATE UNIQUE INDEX uq_one_official_final_per_edition
    ON matches(tournament_edition_id)
    WHERE stage = 'FINAL'
      AND result_status = 'OFFICIAL';
