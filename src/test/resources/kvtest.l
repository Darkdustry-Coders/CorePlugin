
# hi
a = hello {b}

b = {a{b}}
c = {if () < () then .{a} else .{b}}

votekick = {each {vote} in {votes} ([gray]- {vote}) split (\n) join (\n)}

[a]
multiline.example = a
