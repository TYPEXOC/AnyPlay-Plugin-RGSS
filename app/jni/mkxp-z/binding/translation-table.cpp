#include "translation-table.h"

#include <algorithm>
#include <atomic>
#include <cstring>
#include <memory>
#include <mutex>
#include <vector>

#ifdef __ANDROID__
#include <jni.h>
#endif

namespace {

const size_t MAX_INDEX_BYTES = 192u * 1024u * 1024u;

struct Entry {
    uint32_t keyOffset;
    uint32_t keyLength;
    uint32_t valueOffset;
    uint32_t valueLength;
    uint64_t order;
    uint8_t encoding;
};

struct TranslationTable {
    std::vector<uint8_t> bytes;
    std::vector<Entry> entries;
};

struct TranslationBuilder {
    TranslationTable table;
    uint64_t nextOrder;

    TranslationBuilder() : nextOrder(0) {}
};

std::mutex builderMutex;
std::shared_ptr<TranslationBuilder> builder;
std::shared_ptr<const TranslationTable> activeTable =
    std::make_shared<TranslationTable>();
std::atomic<uint64_t> tableRevision(0);

uint32_t readU32(const uint8_t *data) {
    return (static_cast<uint32_t>(data[0]) << 24)
        | (static_cast<uint32_t>(data[1]) << 16)
        | (static_cast<uint32_t>(data[2]) << 8)
        | static_cast<uint32_t>(data[3]);
}

int compareKey(const TranslationTable &table, const Entry &entry,
               const char *key, size_t keyLength) {
    const size_t common = std::min(static_cast<size_t>(entry.keyLength), keyLength);
    const int bytes = common == 0 ? 0 : std::memcmp(
        &table.bytes[entry.keyOffset], key, common);
    if (bytes != 0) return bytes;
    if (entry.keyLength < keyLength) return -1;
    if (entry.keyLength > keyLength) return 1;
    return 0;
}

bool entryLess(const TranslationTable &table, const Entry &left, const Entry &right) {
    const size_t common = std::min(left.keyLength, right.keyLength);
    const int bytes = common == 0 ? 0 : std::memcmp(
        &table.bytes[left.keyOffset], &table.bytes[right.keyOffset], common);
    if (bytes != 0) return bytes < 0;
    if (left.keyLength != right.keyLength) return left.keyLength < right.keyLength;
    return left.order < right.order;
}

} // namespace

bool tynTranslationBeginUpdate() {
    std::lock_guard<std::mutex> lock(builderMutex);
    try {
        builder = std::make_shared<TranslationBuilder>();
        return true;
    } catch (...) {
        builder.reset();
        return false;
    }
}

bool tynTranslationAppendChunk(const uint8_t *data, size_t length) {
    if (!data && length != 0) return false;
    std::lock_guard<std::mutex> lock(builderMutex);
    if (!builder) return false;

    size_t offset = 0;
    while (offset < length) {
        if (length - offset < 9) return false;
        const uint32_t keyLength = readU32(data + offset);
        const uint32_t valueLength = readU32(data + offset + 4);
        const uint8_t encoding = data[offset + 8];
        offset += 9;
        const uint64_t recordLength = static_cast<uint64_t>(keyLength) + valueLength;
        if (keyLength == 0 || recordLength > length - offset || encoding > 5) return false;

        TranslationTable &table = builder->table;
        const uint64_t nextBytes = static_cast<uint64_t>(table.bytes.size()) + recordLength;
        const uint64_t nextIndexBytes = nextBytes
            + static_cast<uint64_t>(table.entries.size() + 1) * sizeof(Entry);
        if (nextBytes > UINT32_MAX || nextIndexBytes > MAX_INDEX_BYTES) return false;

        Entry entry;
        entry.keyOffset = static_cast<uint32_t>(table.bytes.size());
        entry.keyLength = keyLength;
        try {
            table.bytes.insert(table.bytes.end(), data + offset, data + offset + keyLength);
            offset += keyLength;
            entry.valueOffset = static_cast<uint32_t>(table.bytes.size());
            entry.valueLength = valueLength;
            table.bytes.insert(table.bytes.end(), data + offset, data + offset + valueLength);
            offset += valueLength;
            entry.order = builder->nextOrder++;
            entry.encoding = encoding;
            table.entries.push_back(entry);
        } catch (...) {
            return false;
        }
    }
    return true;
}

bool tynTranslationCommitUpdate() {
    std::shared_ptr<TranslationBuilder> completed;
    {
        std::lock_guard<std::mutex> lock(builderMutex);
        if (!builder) return false;
        completed.swap(builder);
    }

    try {
        std::vector<Entry> &entries = completed->table.entries;
        const TranslationTable &table = completed->table;
        std::sort(entries.begin(), entries.end(), [&table](const Entry &left, const Entry &right) {
            return entryLess(table, left, right);
        });

        size_t writeIndex = 0;
        for (size_t readIndex = 0; readIndex < entries.size();) {
            size_t end = readIndex + 1;
            while (end < entries.size() && compareKey(
                table, entries[readIndex],
                reinterpret_cast<const char *>(&table.bytes[entries[end].keyOffset]),
                entries[end].keyLength) == 0) {
                ++end;
            }
            // Entries in a key group are ordered by source-map/encoding order.
            // The Ruby hash used to keep the last exact byte-key variant.
            entries[writeIndex++] = entries[end - 1];
            readIndex = end;
        }
        entries.resize(writeIndex);

        std::shared_ptr<const TranslationTable> next =
            std::make_shared<TranslationTable>(std::move(completed->table));
        {
            std::atomic_store(&activeTable, next);
            tableRevision.fetch_add(1, std::memory_order_release);
        }
        return true;
    } catch (...) {
        return false;
    }
}

void tynTranslationAbortUpdate() {
    std::lock_guard<std::mutex> lock(builderMutex);
    builder.reset();
}

bool tynTranslationLookup(const char *key, size_t keyLength,
                          std::string &value, int &encoding) {
    if (!key && keyLength != 0) return false;
    std::shared_ptr<const TranslationTable> table = std::atomic_load(&activeTable);
    if (!table || table->entries.empty()) return false;

    size_t first = 0;
    size_t last = table->entries.size();
    while (first < last) {
        const size_t middle = first + (last - first) / 2;
        const int comparison = compareKey(*table, table->entries[middle], key, keyLength);
        if (comparison < 0) first = middle + 1;
        else last = middle;
    }
    if (first >= table->entries.size()
        || compareKey(*table, table->entries[first], key, keyLength) != 0) return false;

    const Entry &entry = table->entries[first];
    value.assign(reinterpret_cast<const char *>(&table->bytes[entry.valueOffset]), entry.valueLength);
    encoding = entry.encoding;
    return true;
}

uint64_t tynTranslationRevision() {
    return tableRevision.load(std::memory_order_acquire);
}

size_t tynTranslationEntryCount() {
    std::shared_ptr<const TranslationTable> table = std::atomic_load(&activeTable);
    return table ? table->entries.size() : 0;
}

size_t tynTranslationMemoryBytes() {
    std::shared_ptr<const TranslationTable> table = std::atomic_load(&activeTable);
    return table ? table->bytes.size() + table->entries.size() * sizeof(Entry) : 0;
}

#ifdef __ANDROID__
extern "C" JNIEXPORT jboolean JNICALL
Java_com_hatkid_mkxpz_MainActivity_nativeBeginTranslationUpdate(JNIEnv *, jclass) {
    return tynTranslationBeginUpdate() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hatkid_mkxpz_MainActivity_nativeAppendTranslationChunk(
    JNIEnv *env, jclass, jbyteArray chunk) {
    if (!chunk) return JNI_FALSE;
    const jsize length = env->GetArrayLength(chunk);
    jbyte *bytes = env->GetByteArrayElements(chunk, NULL);
    if (!bytes) return JNI_FALSE;
    const bool result = tynTranslationAppendChunk(
        reinterpret_cast<const uint8_t *>(bytes), static_cast<size_t>(length));
    env->ReleaseByteArrayElements(chunk, bytes, JNI_ABORT);
    return result ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_hatkid_mkxpz_MainActivity_nativeCommitTranslationUpdate(JNIEnv *, jclass) {
    return tynTranslationCommitUpdate() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_hatkid_mkxpz_MainActivity_nativeAbortTranslationUpdate(JNIEnv *, jclass) {
    tynTranslationAbortUpdate();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_hatkid_mkxpz_MainActivity_nativeTranslationTableRevision(JNIEnv *, jclass) {
    return static_cast<jlong>(tynTranslationRevision());
}
#endif
