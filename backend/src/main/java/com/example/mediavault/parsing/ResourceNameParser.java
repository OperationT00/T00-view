package com.example.mediavault.parsing;

import java.text.Normalizer;
import java.util.*;

/** Pure, source-independent parser. No filesystem, network, database or Spring dependencies. */
public final class ResourceNameParser {
    public static final String VERSION = "name-v1";
    public enum Dimension { TYPE, WORK, CHARACTER, SKIN, CLOTHING, CREATOR }
    public record Entry(String id, Dimension dimension, String name, String context,
                        String workId, String characterId, boolean selectable, long version) {}
    /** classificationFolder is a single explicitly selected folder name, not an arbitrary ancestor path. */
    public record Input(String key, String fileName, String relativePath, String classificationFolder) {}
    public record Candidate(Dimension dimension, String text, String origin, String status,
                            List<Entry> matches, String ownerText) {}
    public record Issue(String code, String text, String message) {}
    public record Result(String key, String fileName, String relativePath, String kind,
                         List<Candidate> candidates, List<Issue> issues) {}
    private static final Set<String> VIDEO = Set.of("mp4", "mkv", "avi", "mov", "webm", "m4v", "wmv", "flv");
    private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif");
    private static final Set<String> TYPES = Set.of("cos", "zw", "do");
    private static final Set<String> CLOTHES = Set.of("高跟鞋", "黑丝", "白丝", "旗袍");

    public Result parse(Input input, List<Entry> vocabulary) {
        Objects.requireNonNull(input, "缺少文件信息");
        String file = checked(input.fileName(), 255, true);
        String path = checked(input.relativePath(), 4096, false);
        String folder = checked(input.classificationFolder(), 512, false);
        checked(input.key(), 128, false);
        if (file.contains("/") || file.contains("\\") || folder.contains("/") || folder.contains("\\"))
            throw new IllegalArgumentException("文件名和分类文件夹必须是单个名称，路径请单独填写");
        var state = new State(List.copyOf(vocabulary));
        String stem = file;
        String kind = "OTHER";
        int dot = file.lastIndexOf('.');
        if (dot > 0) {
            String extension = normalize(file.substring(dot + 1));
            if (VIDEO.contains(extension) || IMAGES.contains(extension) || Set.of("zip", "rar", "7z").contains(extension)) {
                stem = file.substring(0, dot);
                if (VIDEO.contains(extension)) kind = "VIDEO";
                else if (IMAGES.contains(extension)) state.issue("IMAGE_GROUP_REQUIRED", file, "图片需要在导入阶段确认单图或图集分组");
            } else state.issue("UNKNOWN_EXTENSION", file, "未知扩展名，保留原名称并按其他文件提示");
        }
        state.directory(folder);
        state.filename(stem);
        return new Result(input.key(), file, path, kind, List.copyOf(state.candidates), List.copyOf(state.issues));
    }

    private static String checked(String value, int max, boolean required) {
        String text = value == null ? "" : value.strip();
        if ((required && text.isEmpty()) || text.length() > max || text.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("名称或路径为空、超长或包含控制字符");
        return text;
    }
    static String normalize(String value) { return Normalizer.normalize(value.strip(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT); }
    private static List<String> parts(String text) {
        var values = Arrays.stream(text.split("-", -1)).map(String::strip).toList();
        if (values.size() > 32) throw new IllegalArgumentException("单个名称最多支持 32 个横杠分段");
        return values;
    }

    private static final class State {
        final List<Entry> vocabulary;
        final List<Candidate> candidates = new ArrayList<>();
        final List<Issue> issues = new ArrayList<>();
        String work = "", character = "", skin = "";
        boolean ambiguousDirectory;
        State(List<Entry> vocabulary) { this.vocabulary = vocabulary; }
        void issue(String code, String text, String message) { issues.add(new Issue(code, text, message)); }
        List<Entry> named(Dimension dimension, String text) {
            return vocabulary.stream().filter(e -> e.dimension() == dimension && normalize(e.name()).equals(normalize(text))).toList();
        }
        List<Entry> scoped(Dimension dimension, String text) {
            var found = named(dimension, text);
            if (dimension == Dimension.CHARACTER && !work.isEmpty()) {
                var works = named(Dimension.WORK, work).stream().map(Entry::id).toList();
                found = found.stream().filter(e -> works.contains(e.workId())).toList();
            }
            if (dimension == Dimension.SKIN && !character.isEmpty()) {
                var owners = scoped(Dimension.CHARACTER, character).stream().map(Entry::id).toList();
                found = found.stream().filter(e -> owners.contains(e.characterId())).toList();
            }
            return found;
        }
        void candidate(Dimension dimension, String text, String origin) {
            if (text.isBlank()) return;
            var found = scoped(dimension, text);
            String status = found.isEmpty() ? "PROPOSED" : found.size() > 1 ? "AMBIGUOUS" : found.getFirst().selectable() ? "MATCHED" : "DISABLED";
            if (status.equals("AMBIGUOUS")) issue("AMBIGUOUS_ENTITY", text, "有多个同名记录，需要确认具体身份");
            if (status.equals("DISABLED")) issue("DISABLED_ENTITY", text, "记录或归属已停用，不能作为新增关联");
            candidates.add(new Candidate(dimension, text, origin, status, found,
                    dimension == Dimension.SKIN ? character : dimension == Dimension.CHARACTER ? work : ""));
        }
        void directory(String folder) {
            if (folder.isBlank()) { issue("NO_CONTEXT", "", "未指定分类文件夹，仅解析文件名可识别部分"); return; }
            var p = parts(folder);
            if (p.stream().anyMatch(String::isEmpty)) { ambiguousDirectory = true; issue("INVALID_FOLDER", folder, "目录字段为空，请明确作品、角色及可选皮肤"); return; }
            // Enumerate boundaries to preserve known entity names containing hyphens.
            List<List<String>> choices = new ArrayList<>();
            int best = -1;
            for (int first = 1; first < p.size(); first++) {
                for (int second = first + 1; second <= p.size(); second++) {
                    String w = String.join("-", p.subList(0, first));
                    String c = String.join("-", p.subList(first, second));
                    String s = second == p.size() ? "" : String.join("-", p.subList(second, p.size()));
                    var ws = named(Dimension.WORK, w).stream().map(Entry::id).toList();
                    var cs = named(Dimension.CHARACTER, c).stream().filter(e -> ws.contains(e.workId())).map(Entry::id).toList();
                    int score = (ws.isEmpty() ? 0 : 1) + (cs.isEmpty() ? 0 : 1)
                            + (s.isEmpty() || named(Dimension.SKIN, s).stream().noneMatch(e -> cs.contains(e.characterId())) ? 0 : 1);
                    if (score > best) { choices.clear(); best = score; }
                    if (score == best) choices.add(List.of(w, c, s));
                }
            }
            List<String> selected;
            if (choices.size() == 1) selected = choices.getFirst();
            else if (best == 0 && (p.size() == 2 || p.size() == 3)) {
                selected = List.of(p.get(0), p.get(1), p.size() == 3 ? p.get(2) : "");
                issue("TEMPLATE_ASSUMPTION", folder, "按作品-角色-可选皮肤模板分段；名称含横杠时请人工核对");
            } else { ambiguousDirectory = true; issue("AMBIGUOUS_FOLDER", folder, "目录分段不唯一，请先明确分类上下文"); return; }
            work = selected.get(0); character = selected.get(1); skin = selected.get(2);
            candidate(Dimension.WORK, work, "FOLDER"); candidate(Dimension.CHARACTER, character, "FOLDER"); candidate(Dimension.SKIN, skin, "FOLDER");
        }
        void filename(String stem) {
            var p = parts(stem);
            if (p.stream().anyMatch(String::isEmpty)) { issue("EMPTY_SEGMENT", stem, "文件名有空字段，请核对分隔符"); return; }
            int creatorEnd = 1;
            for (int end = p.size(); end > 0; end--) {
                if (!named(Dimension.CREATOR, String.join("-", p.subList(0, end))).isEmpty()) { creatorEnd = end; break; }
            }
            String creator = String.join("-", p.subList(0, creatorEnd));
            candidate(Dimension.CREATOR, creator, "FILE");
            if (named(Dimension.CREATOR, creator).isEmpty()) issue("CREATOR_ASSUMPTION", creator, "首段作为创作者候选；名称含横杠或含多位作者时请修正");
            for (int index = creatorEnd; index < p.size();) {
                String token = p.get(index);
                List<Dimension> matched = List.of();
                int next = index + 1;
                for (int end = p.size(); end > index; end--) {
                    String text = String.join("-", p.subList(index, end));
                    var dimensions = new ArrayList<Dimension>();
                    for (var d : List.of(Dimension.SKIN, Dimension.CLOTHING, Dimension.TYPE))
                        if (!named(d, text).isEmpty() || d == Dimension.TYPE && TYPES.contains(normalize(text))
                                || d == Dimension.CLOTHING && CLOTHES.contains(normalize(text))
                                || d == Dimension.SKIN && !skin.isEmpty() && normalize(skin).equals(normalize(text))) dimensions.add(d);
                    if (!dimensions.isEmpty()) { token = text; matched = dimensions; next = end; break; }
                }
                if (matched.size() > 1) issue("AMBIGUOUS_DIMENSION", token, "同时匹配多个维度，不能自动决定归类");
                else if (matched.size() == 1) {
                    var d = matched.getFirst();
                    if (d == Dimension.SKIN) {
                        if (ambiguousDirectory || character.isEmpty()) issue("SKIN_OWNER_REQUIRED", token, "皮肤需要明确角色上下文");
                        else if (!skin.isEmpty() && !normalize(skin).equals(normalize(token))) issue("SKIN_CONFLICT", token, "文件皮肤与目录皮肤不同");
                        else if (scoped(d, token).isEmpty() && !named(d, token).isEmpty()) issue("SKIN_OWNER_CONFLICT", token, "已有同名皮肤属于其他角色，请人工确认");
                        else { candidate(d, token, "FILE"); if (skin.isEmpty()) skin = token; }
                    } else candidate(d, token, "FILE");
                } else if (index == creatorEnd && skin.isEmpty() && !character.isEmpty() && !ambiguousDirectory) {
                    candidate(Dimension.SKIN, token, "FILE"); skin = token;
                    issue("SKIN_ASSUMPTION", token, "目录无皮肤，首个未识别字段仅建议为皮肤，须人工确认");
                } else issue("UNRECOGNIZED", token, "未识别片段，保留原文待确认");
                index = next;
            }
        }
    }
}
