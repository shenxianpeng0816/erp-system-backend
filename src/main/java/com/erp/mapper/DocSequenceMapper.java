package com.erp.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * Provides DB-backed document number sequences that survive restarts.
 * Uses a SELECT ... FOR UPDATE to ensure uniqueness under concurrent access.
 */
@Mapper
public interface DocSequenceMapper {

    /**
     * Atomically increment the counter for the given prefix and date part.
     * Returns the value BEFORE increment (i.e. the number to use NOW).
     */
    @Select("SELECT next_val FROM doc_sequence WHERE seq_name = #{seqName} FOR UPDATE")
    Long getCurrentVal(@Param("seqName") String seqName);

    @Update("UPDATE doc_sequence SET next_val = next_val + 1 WHERE seq_name = #{seqName}")
    void increment(@Param("seqName") String seqName);

    @Select("SELECT date_part FROM doc_sequence WHERE seq_name = #{seqName}")
    String getDatePart(@Param("seqName") String seqName);

    @Update("UPDATE doc_sequence SET next_val = 1, date_part = #{datePart} WHERE seq_name = #{seqName}")
    void resetForNewDay(@Param("seqName") String seqName, @Param("datePart") String datePart);
}
