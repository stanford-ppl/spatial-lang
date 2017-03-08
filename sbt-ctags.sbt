import net.ceedubs.sbtctags.CtagsKeys

// Only generate tag for scala source 
CtagsKeys.ctagsParams ~= (_.copy(languages = Seq("scala")))
