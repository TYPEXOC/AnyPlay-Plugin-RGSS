#ifndef TYN_TRANSLATION_TABLE_H
#define TYN_TRANSLATION_TABLE_H

#include <cstddef>
#include <cstdint>
#include <string>

bool tynTranslationBeginUpdate();
bool tynTranslationAppendChunk(const uint8_t *data, size_t length);
bool tynTranslationCommitUpdate();
void tynTranslationAbortUpdate();
bool tynTranslationLookup(const char *key, size_t keyLength,
                          std::string &value, int &encoding);
uint64_t tynTranslationRevision();
size_t tynTranslationEntryCount();
size_t tynTranslationMemoryBytes();

#endif
