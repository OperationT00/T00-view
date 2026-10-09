package com.example.mediavault.parsing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.example.mediavault.parsing.ResourceNameParser.*;

class ResourceNameParserTest {
    final ResourceNameParser parser = new ResourceNameParser();
    final List<Entry> vocabulary = List.of(
        e("1", Dimension.WORK, "王者荣耀", null, null, true),
        e("2", Dimension.CHARACTER, "公孙离", "1", null, true),
        e("3", Dimension.SKIN, "离恨烟", "1", "2", true),
        e("4", Dimension.SKIN, "其他造型", "1", "2", true),
        e("5", Dimension.CREATOR, "博主-甲", null, null, true));
    static Entry e(String id, Dimension d, String name, String work, String role, boolean enabled) { return new Entry(id,d,name,name,work,role,enabled,0); }
    Result parse(String folder, String file) { return parser.parse(new Input("a",file,"D:/仅文字/"+file,folder),vocabulary); }
    @Test void folderSkinAndMultipleClothesAndType() {
        var result = parse("王者荣耀-公孙离-离恨烟", "博主-甲-高跟鞋-黑丝-zw.mp4");
        assertThat(result.kind()).isEqualTo("VIDEO");
        assertThat(result.issues()).isEmpty();
        assertThat(result.candidates()).filteredOn(c->c.dimension()==Dimension.CREATOR).singleElement().satisfies(c->assertThat(c.text()).isEqualTo("博主-甲"));
        assertThat(result.candidates()).filteredOn(c->c.dimension()==Dimension.CLOTHING).hasSize(2);
        assertThat(result.candidates()).filteredOn(c->c.dimension()==Dimension.TYPE).singleElement().satisfies(c->assertThat(c.text()).isEqualTo("zw"));
    }
    @Test void fileProvidesMissingSkin() {
        assertThat(parse("王者荣耀-公孙离", "博主-甲-离恨烟-zw.mp4").candidates()).filteredOn(c->c.dimension()==Dimension.SKIN).singleElement().satisfies(c->{assertThat(c.status()).isEqualTo("MATCHED");assertThat(c.origin()).isEqualTo("FILE");});
    }
    @Test void skinConflictIsNotSilentlyOverwritten() {
        var result=parse("王者荣耀-公孙离-离恨烟", "博主-甲-其他造型.mp4");
        assertThat(result.issues()).anyMatch(i->i.code().equals("SKIN_CONFLICT"));
        assertThat(result.candidates()).filteredOn(c->c.dimension()==Dimension.SKIN).hasSize(1);
    }
    @Test void noContextDoesNotGuessSkinOwner() { assertThat(parse("", "博主-甲-离恨烟.mp4").issues()).anyMatch(i->i.code().equals("SKIN_OWNER_REQUIRED")); }
    @Test void emptyVocabularyProducesExplicitProposals() {
        var r=parser.parse(new Input("a","博主甲-离恨烟-zw.mp4","","王者荣耀-公孙离"),List.of());
        assertThat(r.candidates()).allMatch(c->c.status().equals("PROPOSED"));
        assertThat(r.issues()).anyMatch(i->i.code().equals("SKIN_ASSUMPTION"));
    }
    @Test void sameNameCreatorsRemainAmbiguous() {
        var r=parser.parse(new Input("a","甲-zw.mp4","",""),List.of(e("1",Dimension.CREATOR,"甲",null,null,true),e("2",Dimension.CREATOR,"甲",null,null,true)));
        assertThat(r.candidates()).filteredOn(c->c.dimension()==Dimension.CREATOR).singleElement().satisfies(c->assertThat(c.status()).isEqualTo("AMBIGUOUS"));
    }
    @Test void disabledRecordStaysDisabled() {
        var r=parser.parse(new Input("a","甲-zw.mp4","",""),List.of(e("1",Dimension.TYPE,"zw",null,null,false)));
        assertThat(r.issues()).anyMatch(i->i.code().equals("DISABLED_ENTITY"));
    }
    @Test void unknownSegmentsArePreserved() { assertThat(parse("王者荣耀-公孙离-离恨烟","博主-甲-未识别.mp4").issues()).anyMatch(i->i.text().equals("未识别")&&i.code().equals("UNRECOGNIZED")); }
    @Test void rejectsControlCharactersAndTooManyTokens() {
        assertThatThrownBy(()->parse("", "a\n.mp4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->parse("", "a-".repeat(40)+"zw.mp4")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unknownExtensionIsNotStrippedAndImagesRequireGrouping() {
        assertThat(parse("", "甲-zw.mystery").issues()).anyMatch(i->i.code().equals("UNKNOWN_EXTENSION"));
        assertThat(parse("", "甲-zw.jpg").issues()).anyMatch(i->i.code().equals("IMAGE_GROUP_REQUIRED"));
    }
    @Test void otherRoleSkinCannotBeMatched() {
        var r=parser.parse(new Input("a","甲-离恨烟.mp4","","王者荣耀-另一角色"),vocabulary);
        assertThat(r.issues()).anyMatch(i->i.code().equals("SKIN_OWNER_CONFLICT"));
    }
    @Test void multipleDimensionMatchesAreNotGuessed() {
        var r=parser.parse(new Input("a","甲-zw.mp4","",""),List.of(e("1",Dimension.CLOTHING,"zw",null,null,true)));
        assertThat(r.issues()).anyMatch(i->i.code().equals("AMBIGUOUS_DIMENSION"));
    }
}
