package com.error22.smt.remapper;

import com.error22.smt.remapper.parser.AstralMapParser;

import java.util.List;

/**
 * Used by SMRemapper to scan classes.
 */
public abstract class AbstractScanner {
    private final AstralMapParser.MapFileContext map;

    public AbstractScanner(AstralMapParser.MapFileContext mapfile) {
        this.map = mapfile;
    }

    protected abstract void processBody(String oldname, String newname, List<AstralMapParser.ClassBodyContext> lcbc);

    public void scanClasses() {
        for (AstralMapParser.ClassDeclarationContext cdc: map.classDeclaration()) {
            // getText() omits whitespace, but that's good here
            String oldname = cdc.oldname.getText();
            String newname;
            if (cdc.newname != null) {
                newname = cdc.newname.getText();
            } else {
                newname = oldname;
            }

            processBody(oldname, newname, cdc.classBody());

            for (AstralMapParser.ClassBodyContext cbc: cdc.classBody()) {
                if (cbc.subclassDeclaration() != null) {
                    scanSubclass(oldname, newname, cbc.subclassDeclaration());
                }
            }
        }
    }

    // Pretty much identical to the loop body of scanClasses(), but includes the doting
    private void scanSubclass(String oldparent, String newparent, AstralMapParser.SubclassDeclarationContext sdc) {
        String oldname = oldparent + "." + sdc.newname.getText();
        String newname;
        if (sdc.newname != null) {
            newname = newparent + "." + sdc.newname.getText();
        } else {
            newname = oldname;
        }

        processBody(oldname, newname, sdc.classBody());

        for (AstralMapParser.ClassBodyContext cbc: sdc.classBody()) {
            if (cbc.subclassDeclaration() != null) {
                scanSubclass(oldname, newname, cbc.subclassDeclaration());
            }
        }
    }
}
